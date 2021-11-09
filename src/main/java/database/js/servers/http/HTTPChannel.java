/*
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.

 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package database.js.servers.http;

import java.net.Socket;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import java.util.logging.Level;
import java.util.logging.Logger;
import database.js.config.Config;
import database.js.servers.Server;
import java.net.InetSocketAddress;
import database.js.pools.ThreadPool;
import javax.net.ssl.SSLEngineResult;
import java.nio.channels.SocketChannel;
import database.js.security.PKIContext;
import java.nio.channels.ClosedChannelException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;


public class HTTPChannel
{
  private boolean connected;

  private final boolean ssl;
  private final boolean admin;
  private final boolean reqssl;

  private final Server server;
  private final Config config;
  private final Logger logger;
  private final SSLEngine engine;
  private final ThreadPool workers;
  private final HTTPBuffers buffers;
  private final SocketChannel channel;


  public HTTPChannel(Server server, SocketChannel channel, boolean ssl) throws Exception
  {
    this.ssl = ssl;
    this.admin = false;
    this.workers = null;
    this.server = server;
    this.channel = channel;
    this.connected = false;
    this.config = server.config();
    this.logger = config.getLogger().intern;
    this.reqssl = config.getHTTP().requiressl();

    if (!ssl)
    {
      this.engine = null;
      this.buffers = new HTTPBuffers();
      channel.socket().setSendBufferSize(buffers.size());
      channel.socket().setReceiveBufferSize(buffers.size());
    }
    else
    {
      PKIContext pki = config.getPKIContext();
      this.engine = pki.getSSLContext().createSSLEngine();

      this.engine.setUseClientMode(true);
      this.buffers = new HTTPBuffers(appsize(),packsize());

      channel.socket().setSendBufferSize(packsize());
      channel.socket().setReceiveBufferSize(packsize());
    }
  }


  public HTTPChannel(Server server, ThreadPool workers, SocketChannel channel, boolean ssl, boolean admin) throws Exception
  {
    this.ssl = ssl;
    this.admin = admin;
    this.server = server;
    this.workers = workers;
    this.channel = channel;
    this.connected = false;
    this.config = server.config();
    this.logger = config.getLogger().intern;
    this.reqssl = config.getHTTP().requiressl();

    if (!ssl)
    {
      this.engine = null;
      this.buffers = new HTTPBuffers();
      channel.socket().setSendBufferSize(buffers.size());
      channel.socket().setReceiveBufferSize(buffers.size());
    }
    else
    {
      PKIContext pki = config.getPKIContext();
      this.engine = pki.getSSLContext().createSSLEngine();

      this.engine.setUseClientMode(false);
      this.engine.setNeedClientAuth(admin);
      this.buffers = new HTTPBuffers(appsize(),packsize());

      channel.socket().setSendBufferSize(packsize());
      channel.socket().setReceiveBufferSize(packsize());
    }
  }


  boolean ssl()
  {
    return(ssl);
  }


  boolean admin()
  {
    return(admin);
  }


  boolean redirect()
  {
    if (ssl || admin) return(false);
    else              return(reqssl);
  }


  public Socket socket()
  {
    return(channel.socket());
  }


  public Server server()
  {
    return(server);
  }


  public Config config()
  {
    return(config);
  }


  Logger logger()
  {
    return(logger);
  }


  ThreadPool workers()
  {
    return(workers);
  }


  SocketChannel channel()
  {
    return(channel);
  }


  void failed()
  {
    try {channel.close();}
    catch (Exception e) {;}
  }


  public boolean connected()
  {
    return(connected);
  }


  public void connect(int port) throws Exception
  {
    connect("localhost",port);
  }


  public void connect(String host, int port) throws Exception
  {
    channel.connect(new InetSocketAddress(host,port));
    while(!channel.finishConnect()) Thread.sleep(1);

    if (ssl)
    {
      this.accept();
      Thread.sleep(1);
    }
  }


  public void close() throws Exception
  {
    this.channel.close();
  }


  public void configureBlocking(boolean block) throws Exception
  {
    this.channel.configureBlocking(block);
  }


  public ByteBuffer read()
  {
    ByteBuffer buf = null;

    try
    {
      if (ssl) buf = readssl();
      else     buf = readplain();
    }
    catch (Throwable e)
    {
      logger.log(Level.WARNING,e.getMessage(),e);
    }

    return(buf);
  }


  private ByteBuffer readplain() throws Exception
  {
    try
    {
      buffers.alloc();
      int read = channel.read(buffers.data);

      if (read <= 0)
      {
        buffers.done();
        return(null);
      }

      if (buffers.data == null)
        return(buffers.done());

      buffers.data.flip();
      return(buffers.done());
    }
    catch (Exception e)
    {
      buffers.done();
      String msg = e.getMessage();
      if (msg == null) msg = "unknown";
      if (!msg.equals("Connection reset")) throw e;
      return(null);
    }
  }


  private ByteBuffer readssl() throws Exception
  {
    try
    {
      buffers.alloc();
      buffers.sslb.clear();

      int read = channel.read(buffers.sslb);

      if (read <= 0)
      {
        buffers.done();
        return(null);
      }

      buffers.sslb.flip();
      while(buffers.sslb.hasRemaining())
      {
        SSLEngineResult result = engine.unwrap(buffers.sslb,buffers.data);

        switch(result.getStatus())
        {
          case OK:
            break;

          case BUFFER_OVERFLOW:
            buffers.data = enlarge(buffers.data,appsize());
            break;

          case BUFFER_UNDERFLOW:
            if (buffers.sslb.limit() < packsize())
              buffers.sslb = enlarge(buffers.sslb,packsize());
            break;

          case CLOSED:
            buffers.done();
            return(null);
        }
      }
    }
    catch (Exception e)
    {
      handle(e);
      buffers.done();
      return(null);
    }

    buffers.data.flip();
    return(buffers.done());
  }


  public void write(ByteBuffer buf) throws Exception
  {
    int read = buf.remaining();
    byte[] data = new byte[read]; buf.get(data);
    write(data);
  }


  public void write(byte[] data) throws Exception
  {
    int wrote = 0;
    buffers.alloc();
    Socket socket = channel.socket();
    int max = buffers.data.capacity();

    int size = data.length;
    int bsize = buffers.data.capacity();

    while(wrote < size)
    {
      int chunk = bsize;

      if (chunk > size - wrote)
        chunk = size - wrote;

      if (chunk > max)
        chunk = max;

      buffers.data.put(data,wrote,chunk);
      buffers.data.flip();

      if (ssl) writessl();
      else     writeplain();

      if (!socket.isClosed())
      {
        try {socket.getOutputStream().flush();}
        catch (Exception e) {;}
      }

      wrote += chunk;
      buffers.alloc(true);
    }

    buffers.done();
  }


  private void writeplain() throws Exception
  {
    try
    {
      int remain = buffers.data.remaining();
      while(remain > 0) remain -= channel.write(buffers.data);
    }
    catch (Exception e)
    {
      if (e instanceof ClosedChannelException)
      {
        logger.warning("Client closed connection");
        return;
      }

      throw e;
    }
  }


  private void writessl() throws Exception
  {
    int remain = buffers.data.remaining();

    while(remain > 0)
    {
      buffers.sslb.clear();
      SSLEngineResult result = engine.wrap(buffers.data,buffers.sslb);

      switch(result.getStatus())
      {
        case OK:
          buffers.sslb.flip();

          while(buffers.sslb.hasRemaining())
            remain -= channel.write(buffers.sslb);

          break;

        case BUFFER_OVERFLOW:
          buffers.sslb = enlarge(buffers.sslb,packsize());
          break;

        case BUFFER_UNDERFLOW:
          buffers.done();
          throw new IllegalStateException("Unexpected behaivior");
      }
    }
  }


  boolean accept() throws Exception
  {
    if (ssl) return(sslaccept());
    else     return(plainaccept());
  }


  private boolean plainaccept()
  {
    this.connected = true;
    return(this.connected);
  }


  private boolean sslaccept() throws Exception
  {
    int read;
    boolean cont = true;

    SSLEngineResult result = null;
    HandshakeStatus status = null;

    buffers.handshake();

    try
    {
      engine.beginHandshake();

      while(cont)
      {
        status = engine.getHandshakeStatus();

        switch(status)
        {
          case NEED_UNWRAP:
            read = channel.read(buffers.recv);

            if (read < 0)
            {
              if (engine.isInboundDone() && engine.isOutboundDone())
              {
                this.buffers.done();
                this.connected = true;
                return(this.connected);
              }

              try {engine.closeInbound();}
              catch (Exception e) {;}

              engine.closeOutbound();
              break;
            }

            buffers.recv.flip();

            try
            {
              result = engine.unwrap(buffers.recv,buffers.data);
              buffers.recv.compact();
            }
            catch (Exception e)
            {
              handle(e);
              this.buffers.done();
              engine.closeOutbound();
              this.connected = false;
              return(this.connected);
            }

            switch(result.getStatus())
            {
              case OK:
                break;

              case BUFFER_OVERFLOW:
                buffers.data = enlarge(buffers.data,appsize());
                break;

              case BUFFER_UNDERFLOW:
                if (buffers.recv.limit() < packsize())
                  buffers.recv = enlarge(buffers.recv,packsize());
                break;

              case CLOSED:
                if (engine.isOutboundDone())
                  engine.closeOutbound();
                break;

              default:
                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
            break;

          case NEED_WRAP:
            buffers.send.clear();

            try
            {
              result = engine.wrap(buffers.data,buffers.send);
            }
            catch (Exception e)
            {
              handle(e);
              this.buffers.done();
              engine.closeOutbound();
              this.connected = false;
              return(this.connected);
            }

            switch(result.getStatus())
            {
              case OK:
                buffers.send.flip();

                while(buffers.send.hasRemaining())
                  channel.write(buffers.send);
                break;

              case BUFFER_OVERFLOW:
                buffers.send = enlarge(buffers.send,packsize());
                break;

              case BUFFER_UNDERFLOW:
                throw new IllegalStateException("Unexpected behaivior");

              case CLOSED:
                try
                {
                  buffers.send.flip();

                  while(buffers.send.hasRemaining())
                    channel.write(buffers.send);

                  buffers.send.clear();
                  break;
                }
                catch (Exception e)
                {
                  logger.warning("Failed to send server's CLOSE message");
                }
                break;

              default:
                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
            break;

          case NEED_TASK:
            Runnable task = engine.getDelegatedTask();

            while(task != null)
            {
              task.run();
              task = engine.getDelegatedTask();
            }
            break;

          case FINISHED:
            cont = false;
            break;

          case NOT_HANDSHAKING:
            cont = false;
            break;

          default:
            throw new IllegalStateException("Invalid SSL status: "+status);
        }
      }
    }
    catch (Exception e)
    {
      try {this.channel.close();}
      catch (Exception chc) {;}
      logger.log(Level.SEVERE,e.getMessage(),e);
    }

    this.buffers.done();

    if (result == null)
    {
      this.connected = true;
      return(this.connected);
    }

    this.connected = result.getStatus() == SSLEngineResult.Status.OK;
    return(this.connected);
  }


  private void handle(Exception e)
  {
    boolean skip = false;
    String errm = e.getMessage();
    if (errm == null) errm = "An unknown error has occured";
    if (errm.startsWith("Received fatal alert: certificate_unknown")) skip = true;
    if (!skip) logger.log(Level.SEVERE,e.getMessage(),e);
  }


  private ByteBuffer enlarge(ByteBuffer buf, int size) throws Exception
  {
    ByteBuffer bufc = buf;
    int left = buf.remaining();

    if (left < size)
    {
      buf = ByteBuffer.allocateDirect(buf.position() + size);
      bufc.flip();
      buf.put(bufc);
    }

    return(buf);
  }


  private int packsize()
  {
    return(engine.getSession().getPacketBufferSize());
  }


  private int appsize()
  {
    return(engine.getSession().getApplicationBufferSize());
  }
}