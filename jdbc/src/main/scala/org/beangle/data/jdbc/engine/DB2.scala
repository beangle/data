/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.engine

import java.sql.Types._

class DB2(v: String) extends AbstractEngine(Version(v)) {
  metadataLoadSql.sequenceSql = "select name as sequence_name,start-1 as current_value,increment,cache from sysibm.syssequences where schema=':schema'"

  registerReserved("db2.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar($l)",
    BOOLEAN -> "smallint", BIT -> "smallint",
    SMALLINT -> "smallint", TINYINT -> "smallint", INTEGER -> "integer", DECIMAL -> "bigint", BIGINT -> "bigint",
    FLOAT -> "float", DOUBLE -> "double", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "timestamp",
    BINARY -> "varchar($l) for bit data",
    VARBINARY -> "varchar($l) for bit data",
    LONGVARCHAR -> "long varchar",
    LONGVARBINARY -> "long varchar for bit data",
    BLOB -> "blob($l)", CLOB -> "clob($l)")

  options.sequence { s =>
    s.nextValSql = "values nextval for {name}"
    s.dropSql = "drop sequence {name} restrict"
    s.selectNextValSql = "nextval for {name}"
  }
  options.comment.supportsCommentOn = true

  // 和 postgresql 比较接近
  options.alter { a =>
    a.table.addColumn = "add {column} {type}"
    a.table.changeType = "alter column {column} set data type {type}"
    a.table.setDefault = "alter column {column} set default {value}"
    a.table.dropDefault = "alter column {column} drop default"
    a.table.setNotNull = "alter column {column} set not null"
    a.table.dropNotNull = "alter column {column} drop not null"
    a.table.dropColumn = "drop column {column}"

    a.table.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.table.dropConstraint = "drop constraint {name}"
  }
  options.validate()

  override def limit(sql: String, offset: Int, limit: Int): (String, List[Int]) = {
    if (offset == 0) {
      (sql + " fetch first " + limit + " rows only", List.empty)
    } else {
      //nest the main query in an outer select
      ("select * from ( select inner2_.*, rownumber() over(order by order of inner2_) as _rownum_ from ( "
        + sql + " fetch first " + limit + " rows only ) as inner2_ ) as inner1_ where _rownum_ > "
        + offset + " order by _rownum_", List.empty)
    }
  }

  override def defaultSchema: String = null

  override def name: String = {
    "DB2"
  }
}
