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

package database.js.database;

import java.util.logging.Level;
import java.util.logging.Logger;
import database.js.config.Config;
import database.js.servers.Server;

import java.util.ArrayList;


public class PoolManager extends Thread
{
  private final Server server;
  private final Config config;
  private final static Logger logger = Logger.getLogger("rest");


  public PoolManager(Server server)
  {
    this(server,false);
  }


  public PoolManager(Server server, boolean start)
  {
    this.server = server;
    this.config = server.config();

    this.setDaemon(true);
    this.setName("PoolManager");

    if (start) this.start();
  }


  @Override
  public void run()
  {
    logger.info("PoolManager started");

    try
    {
      ArrayList<Database> conns = null;
      
      Pool pp = config.getDatabase().proxy;
      Pool ap = config.getDatabase().anonymous;
      
      if (ap == null && pp == null)
        return;

      while(true)
      {
        Thread.sleep(10000);
        
        if (ap != null)
        {
          conns = ap.connections();
          
          for(Database conn : conns)
            System.out.println("a: "+conn);
        }
        
        if (pp != null)
        {
          conns = pp.connections();
          
          for(Database conn : conns)
            System.out.println("p: "+conn);
        }        
      }
    }
    catch (Exception e)
    {
      logger.log(Level.SEVERE,e.getMessage(),e);
    }
  }
}