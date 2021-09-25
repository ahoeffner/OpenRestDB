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

package database.js.servers;

import ipc.Broker;
import ipc.Message;
import ipc.Listener;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import database.js.config.Config;
import database.js.config.Topology;
import database.js.cluster.Cluster;
import static database.js.config.Config.*;
import database.js.servers.http.HTTPServer;
import database.js.servers.http.HTTPServerType;


public class Server extends Thread implements Listener
{
  private final short id;
  private final int heartbeat;
  private final Broker broker;
  private final Logger logger;
  private final Config config;
  private boolean stop = false;
  private final boolean embedded;
  
  private final HTTPServer ssl;
  private final HTTPServer plain;
  private final HTTPServer admin;
  
  
  public static void main(String[] args)
  {
    try
    {
      new Server(Short.parseShort(args[0]));
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }
  
  
  Server(short id) throws Exception
  {
    this.id = id;
    this.config = new Config();
    this.setName("Server Main");
    
    if (Cluster.isRunning(config,id))
      throw new Exception("Server is already running");      

    config.getLogger().open(id);
    this.logger = config.getLogger().logger;    
    Config.Type type = Cluster.getType(config,id);

    Broker.logger(logger);
    boolean master = type == Type.http;
    
    this.heartbeat = config.getIPConfig().heartbeat;
    this.broker = new Broker(config.getIPConfig(),this,id,master);
    this.embedded = config.getTopology().type() == Topology.Type.Micro;

    this.ssl = new HTTPServer(this, HTTPServerType.ssl,embedded);
    this.plain = new HTTPServer(this, HTTPServerType.plain,embedded);
    this.admin = new HTTPServer(this, HTTPServerType.admin,embedded);

    if (broker.manager() && type == Type.http) 
      startup();

    this.start();
  }
  
  
  private void startup()
  {
    ssl.start();
    plain.start();
    admin.start();
    this.ensure();
  }
  
  
  private void ensure()
  {
    logger.info("Starting all instances");
  }
  
  
  public Logger logger()
  {
    return(logger);
  }
  
  
  public short id()
  {
    return(id);
  }
  
  
  public Config config()
  {
    return(config);
  }
  
  
  public Broker broker()
  {
    return(broker);
  }


  @Override
  public void onServerUp(short s)
  {
  }


  @Override
  public void onServerDown(short s)
  {
  }


  @Override
  public void onNewManager(short s)
  {
    if (s == id)
    {
      startup();
      logger.info("Switching to http process "+id);
    }
  }


  @Override
  public void onMessage(ArrayList<Message> arrayList)
  {
  }
  
  
  public void shutdown()
  {
    synchronized(this)
    {
      stop = true;
      this.notify();
    }
  }
  
  
  @Override
  public void run()
  {
    try 
    {
      synchronized(this)
      {
        while(!stop)
        {
          Cluster.setStatistics(this);
          this.wait(this.heartbeat);
        }
      }
    }
    catch (Exception e) {logger.log(Level.SEVERE,e.getMessage(),e);}
    logger.info("Server stopped");
  }
}