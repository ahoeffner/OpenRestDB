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

import java.util.HashMap;
import org.json.JSONArray;
import java.util.ArrayList;
import org.json.JSONObject;
import database.json.database.BindValue;
import database.json.database.BindValueDef;


public class Insert implements SQLObject
{
   private final Source source;
   private final JSONObject payload;
   private final BindValue[] bindvalues;

   private final HashMap<String,BindValue> values =
      new HashMap<String,BindValue>();


   public Insert(JSONObject definition) throws Exception
   {
      String source = null;
      ArrayList<BindValue> bindvalues = new ArrayList<BindValue>();

      if (definition.has(Parser.SOURCE))
         source = definition.getString(Parser.SOURCE);

      if (definition.has(Parser.VALUES))
      {
         JSONArray jarr = definition.getJSONArray(Parser.VALUES);

         for (int i = 0; i < jarr.length(); i++)
         {
            String name = null;
            String type = null;
            Object value = null;
            String column = null;

            JSONObject bdef = jarr.optJSONObject(i);

            if (bdef.has("column"))
               column = bdef.getString("column");

            if (bdef.has(Parser.VALUE))
            {
               bdef = bdef.getJSONObject(Parser.VALUE);

               if (bdef.has("value")) value = bdef.get("value");
               if (bdef.has("name")) name = bdef.getString("name");
               if (bdef.has("type")) type = bdef.getString("type");

               BindValue bval = new BindValue(new BindValueDef(name,type,false,value),false);

               bindvalues.add(bval);
               values.put(column,bval);
            }
         }
      }

      this.payload = definition;
      this.source = Source.getSource(source);

      if (this.source == null)
         throw new Exception(Source.deny(source));

      if (this.source.derived != null)
      {
         for (String col : this.source.derived)
         {
            values.remove(col.toLowerCase());
            continue;
         }
      }

      this.bindvalues = bindvalues.toArray(new BindValue[0]);
   }


   @Override
   public JSONObject payload()
   {
      return(payload);
   }


   @Override
   public boolean validate()
   {
      return(true);
   }


   @Override
   public String sql() throws Exception
   {
      String sql = "insert into "+source.table;

      sql += "(";

      String[] columns = values.keySet().toArray(new String[0]);

      for (int i = 0; i < columns.length; i++)
      {
         if (i > 0) sql += ",";
         sql += columns[i];
      }

      sql += ") values (";

      for (int i = 0; i < columns.length; i++)
      {
         if (i > 0) sql += ",";
         sql += ":"+values.get(columns[i]).getName();
      }

      sql += ")";
      return(sql);
   }


   @Override
   public BindValue[] getBindValues()
   {
      return(bindvalues);
   }


   @Override
   public BindValue[] getAssertions()
   {
      return(new BindValue[0]);
   }

   @Override
   public String path()
   {
      return(Parser.INSERT);
   }

   @Override
   public JSONObject toApi() throws Exception
   {
      JSONObject parsed = Parser.toApi(this);
      return(parsed);
   }

   @Override
   public boolean lock()
   {
      return(false);
   }
}
