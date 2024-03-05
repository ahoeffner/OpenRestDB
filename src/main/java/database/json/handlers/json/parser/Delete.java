/*
  MIT License

  Copyright © 2023 Alex Høffner

  Permission is hereby granted, free of charge, to any person obtaining a copy of this software
  and associated documentation files (the “Software”), to deal in the Software without
  restriction, including without limitation the rights to use, copy, modify, merge, publish,
  distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
  Software is furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all copies or
  substantial portions of the Software.

  THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
  BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package database.json.handlers.json.parser;

import java.util.Arrays;
import java.util.ArrayList;
import org.json.JSONObject;
import database.json.database.BindValue;


public class Delete implements SQLObject
{
   private final boolean lock;
   private final String source;
   private final Object custom;
   private final String session;
   private final WhereClause whcl;
   private final BindValue[] assertions;
   private final BindValue[] bindvalues;


   public Delete(JSONObject definition) throws Exception
   {
      String source = null;
      boolean lock = false;
      Object custom = null;
      String session = null;
      WhereClause whcl = null;

      ArrayList<BindValue> bindvalues = new ArrayList<BindValue>();

      if (definition.has(Parser.LOCK))
         lock = definition.getBoolean(Parser.LOCK);

      if (definition.has(Parser.SOURCE))
         source = definition.getString(Parser.SOURCE);

      if (definition.has(Parser.SESSION))
         session = definition.getString(Parser.SESSION);

      if (definition.has(Parser.FILTERS))
      {
         whcl = new WhereClause(); whcl.parse(definition);
         bindvalues.addAll(Arrays.asList(whcl.getBindValues()));
      }

      bindvalues.addAll(Arrays.asList(Parser.getBindValues(definition)));
      if (definition.has(Parser.CUSTOM)) custom = definition.get(Parser.CUSTOM);

      this.whcl = whcl;
      this.lock = lock;
      this.source = source;
      this.custom = custom;
      this.session = session;
      this.assertions = Parser.getAssertions(definition);
      this.bindvalues = bindvalues.toArray(new BindValue[0]);
   }


   @Override
   public String session()
   {
      return(session);
   }


   @Override
   public boolean validate() throws Exception
   {
      Source source = Source.getSource(this.source);

      if (source == null)
         return(false);

      if (whcl != null)
         return(whcl.validate(source));

      return(true);
   }


   @Override
   public String sql() throws Exception
   {
      String sql = "";
      Source source = Source.getSource(this.source);
      if (source == null) throw new Exception("Permission denied, source: '"+this.source+"'");

      sql += "delete from "+source.table;

      if (whcl != null && !whcl.isEmpty())
         sql += " where " + whcl.sql();

      return(sql);
   }


   @Override
   public Object custom()
   {
      return(custom);
   }


   @Override
   public BindValue[] getBindValues()
   {
      return(bindvalues);
   }


   @Override
   public BindValue[] getAssertions()
   {
      return(assertions);
   }

   @Override
   public String path()
   {
      return(Parser.DELETE);
   }

   @Override
   public JSONObject toApi() throws Exception
   {
      JSONObject parsed = Parser.toApi(this);
      if (lock) parsed.put(Parser.LOCK,lock);
      return(parsed);
   }

   @Override
   public boolean lock()
   {
      return(lock);
   }
}