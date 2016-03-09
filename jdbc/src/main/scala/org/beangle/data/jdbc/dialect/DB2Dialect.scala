/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.dialect

import java.sql.Types

class DB2Dialect extends AbstractDialect("[8.0]") {

  override def sequenceGrammar = {
    val ss = new SequenceGrammar()
    ss.querySequenceSql = "select name as sequence_name,start-1 as current_value,increment,cache from sysibm.syssequences where schema=':schema'"
    ss.nextValSql = "values nextval for :name"
    ss.dropSql = "drop sequence :name restrict"
    ss.selectNextValSql = "nextval for :name"
    ss
  }

  protected override def registerType() = {
    registerType(Types.BOOLEAN, "smallint")
    registerType(Types.BIT, "smallint")
    registerType(Types.DECIMAL, "bigint")
    registerType(Types.BIGINT, "bigint")
    registerType(Types.SMALLINT, "smallint")
    registerType(Types.TINYINT, "smallint")
    registerType(Types.INTEGER, "integer")

    registerType(Types.CHAR, "char($l)")
    registerType(Types.VARCHAR, "varchar($l)")

    registerType(Types.FLOAT, "float")
    registerType(Types.DOUBLE, "double")
    registerType(Types.DATE, "date")
    registerType(Types.TIME, "time")
    registerType(Types.TIMESTAMP, "timestamp")
    registerType(Types.NUMERIC, "numeric($p,$s)")

    registerType(Types.BINARY, "varchar($l) for bit data")
    registerType(Types.VARBINARY, "varchar($l) for bit data")
    registerType(Types.LONGVARCHAR, "long varchar")
    registerType(Types.LONGVARBINARY, "long varchar for bit data")

    registerType(Types.BLOB, "blob($l)")
    registerType(Types.CLOB, "clob($l)")
  }

  /**
   * Render the <tt>rownumber() over ( .... ) as rownumber_,</tt> bit, that
   * goes in the select list
   */
  private def getRowNumber(sql: String) = {
    val rownumber: StringBuilder = new StringBuilder(50).append("rownumber() over(")
    val orderByIndex: Int = sql.toLowerCase().indexOf("order by")
    if (orderByIndex > 0 && !hasDistinct(sql)) {
      rownumber.append(sql.substring(orderByIndex))
    }
    rownumber.append(") as rownumber_,")
    rownumber.toString()
  }

  private def hasDistinct(sql: String) = sql.toLowerCase().indexOf("select distinct") >= 0

  override def limitGrammar = {
    class DB2LimitGrammar extends LimitGrammar {
      override def limit(sql: String, offset: Int, limit: Int): Tuple2[String, List[Int]] = {
        if (offset == 0) {
          (sql + " fetch first " + limit + " rows only", List.empty)
        } else {
          //nest the main query in an outer select
          ("select * from ( select inner2_.*, rownumber() over(order by order of inner2_) as rownumber_ from ( "
            + sql + " fetch first " + limit + " rows only ) as inner2_ ) as inner1_ where rownumber_ > "
            + offset + " order by rownumber_", List.empty)
        }
      }
    }
    new DB2LimitGrammar
  }

  override def defaultSchema: String = null

  override def storeCase: StoreCase.Value = {
    StoreCase.Mixed
  }

}
