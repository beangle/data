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

import org.beangle.data.jdbc.meta.SqlType

import java.sql.Types
import java.sql.Types.*

class PostgreSQL10 extends AbstractEngine {
  registerReserved("postgresql.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar($l)", LONGVARCHAR -> "text",
    NCHAR -> "char($l)", NVARCHAR -> "varchar($l)", LONGNVARCHAR -> "text", //national character
    BOOLEAN -> "boolean", BIT -> "boolean",
    SMALLINT -> "smallint", TINYINT -> "smallint", INTEGER -> "integer", BIGINT -> "bigint",
    REAL -> "float4", FLOAT -> "float4", DOUBLE -> "float8",
    DECIMAL -> "numeric($p,$s)", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "timestamp", TIMESTAMP_WITH_TIMEZONE -> "timestamptz",
    BINARY -> "bytea", VARBINARY -> "bytea", LONGVARBINARY -> "bytea",
    BLOB -> "bytea", CLOB -> "text", NCLOB -> "text",
    JAVA_OBJECT -> "json")

  registerTypes2(
    (VARCHAR, 50000, "varchar($l)"),
    (VARCHAR, Int.MaxValue, "text"),
    (NUMERIC, 1000, "numeric($p, $s)"),
    (NUMERIC, Int.MaxValue, "numeric(1000, $s)"))

  options.sequence { s =>
    s.nextValSql = "select nextval ('{name}')"
    s.selectNextValSql = "nextval ('{name}')"
  }

  options.comment.supportsCommentOn = true
  options.limit { l =>
    l.pattern = "{} limit ?"
    l.offsetPattern = "{} limit ? offset ?"
    l.bindInReverseOrder = true
  }

  options.table.drop.sql = "drop table {name} cascade"
  options.table.truncate.sql = "truncate table {name} cascade"
  options.table.alter { a =>
    a.changeType = "alter {column} type {type}"
    a.setDefault = "alter {column} set default {value}"
    a.dropDefault = "alter {column} drop default"
    a.setNotNull = "alter {column} set not null"
    a.dropNotNull = "alter {column} drop not null"
    a.addColumn = "add {column} {type}"
    a.dropColumn = "drop {column} cascade"
    a.renameColumn = "rename column {oldcolumn} to {newcolumn}"

    a.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.dropConstraint = "drop constraint if exists {name} cascade"
  }
  options.validate()

  functions { f =>
    f.currentDate = "current_date"
    f.localTime = "localtime"
    f.currentTime = "current_time"
    f.localTimestamp = "localtimestamp"
    f.currentTimestamp = "current_timestamp"
  }

  metadataLoadSql.sequenceSql = "select sequence_name,start_value,increment increment_by,cycle_option cycle_flag" +
    " from information_schema.sequences where sequence_schema=':schema'"

  metadataLoadSql.viewDefSql = "select pg_get_viewdef(':schema.:view_name', true) as dfn"

  override def maxIdentifierLength: Int = 63

  override def storeCase: StoreCase = StoreCase.Lower

  override def toType(sqlCode: Int, precision: Int, scale: Int): SqlType = {
    if (sqlCode == BLOB) {
      super.toType(VARBINARY, precision, scale)
    } else {
      super.toType(sqlCode, precision, scale)
    }
  }

  override def defaultSchema: String = "public"

  override def name: String = "PostgreSQL"

  override def version: Version = Version("[10.0,)")

  override def resolveCode(typeCode: Int, typeName: String): Int = {
    typeCode match {
      case TIMESTAMP =>
        typeName match {
          case "timestamptz" => TIMESTAMP_WITH_TIMEZONE
          case _ => TIMESTAMP
        }
      case _ => typeCode
    }
  }

  override def systemSchemas: Seq[String] = List("information_schema", "pg_catalog")
}
