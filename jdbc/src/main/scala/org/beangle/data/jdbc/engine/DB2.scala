/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.jdbc.engine

import java.sql.Types.*

class DB2V8 extends AbstractEngine {

  metadataLoadSql.sequenceSql = "select name as sequence_name,start-1 as current_value,increment,cache from sysibm.syssequences where schema=':schema'"

  registerReserved("db2.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar($l)", LONGVARCHAR -> "long varchar",
    NCHAR -> "char($l)", NVARCHAR -> "varchar($l)", LONGNVARCHAR -> "long varchar",
    BOOLEAN -> "smallint", BIT -> "smallint",
    SMALLINT -> "smallint", TINYINT -> "smallint", INTEGER -> "integer", DECIMAL -> "bigint", BIGINT -> "bigint",
    REAL -> "real", FLOAT -> "real", DOUBLE -> "double", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "timestamp", TIMESTAMP_WITH_TIMEZONE -> "timestamp",
    BINARY -> "varchar($l) for bit data",
    VARBINARY -> "varchar($l) for bit data",
    LONGVARBINARY -> "long varchar for bit data",
    BLOB -> "blob($l)", CLOB -> "clob($l)", NCLOB -> "clob($l)")

  options.sequence { s =>
    s.nextValSql = "values nextval for {name}"
    s.dropSql = "drop sequence {name} restrict"
    s.selectNextValSql = "nextval for {name}"
  }
  options.comment.supportsCommentOn = true

  options.table.truncate.sql = "truncate table {name} immediate"
  // 和 postgresql 比较接近
  options.table.alter { a =>
    a.addColumn = "add {column} {type}"
    a.changeType = "alter column {column} set data type {type}"
    a.setDefault = "alter column {column} set default {value}"
    a.dropDefault = "alter column {column} drop default"
    a.setNotNull = "alter column {column} set not null"
    a.dropNotNull = "alter column {column} drop not null"
    a.dropColumn = "drop column {column}"
    a.renameColumn = "rename column {oldcolumn} to {newcolumn}"

    a.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.dropConstraint = "drop constraint {name}"
  }
  options.validate()

  functions { f =>
    f.currentDate = "current_date"
    f.localTime = "current_time"
    f.currentTime = "current_time"
    f.localTimestamp = "current_timestamp"
    f.currentTimestamp = "current_timestamp"
  }

  override def limit(sql: String, offset: Int, limit: Int): (String, List[Int]) = {
    if (null != options.limit.pattern) return super.limit(sql, offset, limit)

    if (offset == 0) {
      (sql + " fetch first " + limit + " rows only", List.empty)
    } else {
      //nest the main query in an outer select
      ("select * from ( select inner2_.*, rownumber() over(order by order of inner2_) as _rownum_ from ( "
        + sql + " fetch first " + limit + " rows only ) as inner2_ ) as inner1_ where _rownum_ > "
        + offset + " order by _rownum_", List.empty)
    }
  }

  override def version: Version = Version("[8.0]")

  override def defaultSchema: String = null

  override def name: String = "DB2"

  override def systemSchemas: Seq[String] = List("SYS","SYSIBM","SYSTOOLS","SYSCAT", "SYSSTAT")
  override def supportBoolean: Boolean = false
}
