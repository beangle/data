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

import org.beangle.data.jdbc.meta.SqlType

class PostgreSQL(v: String) extends AbstractEngine(Version(v)) {
  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar($l)", LONGVARCHAR -> "text",
    BOOLEAN -> "boolean", BIT -> "boolean",
    SMALLINT -> "smallint", TINYINT -> "smallint", INTEGER -> "integer", BIGINT -> "bigint",
    FLOAT -> "float4", DOUBLE -> "float8",
    DECIMAL -> "numeric($p,$s)", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "timestamp",
    BINARY -> "bytea", VARBINARY -> "bytea", LONGVARBINARY -> "bytea",
    CLOB -> "text", BLOB -> "bytea")

  registerTypes2((NUMERIC, 1000, "numeric($p, $s)"),
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

  options.drop.table.sql = "drop table {name} cascade"

  options.alter { a =>
    a.table.changeType = "alter {column} type {type}"
    a.table.setDefault = "alter {column} set default {value}"
    a.table.dropDefault = "alter {column} drop default"
    a.table.setNotNull = "alter {column} set not null"
    a.table.dropNotNull = "alter {column} drop not null"
    a.table.addColumn = "add {column} {type}"
    a.table.dropColumn = "drop {column} cascade"

    a.table.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.table.dropConstraint = "drop constraint {name} cascade"
  }
  options.validate()

  override def storeCase: StoreCase.Value = {
    StoreCase.Lower
  }

  override def toType(sqlCode: Int, precision: Int, scale: Int): SqlType = {
    if (sqlCode == BLOB) {
      super.toType(VARBINARY, precision, scale)
    } else {
      super.toType(sqlCode, precision, scale)
    }
  }

  override def defaultSchema: String = {
    "public"
  }

  override def name: String = "PostgreSQL"

  metadataLoadSql.sequenceSql = "select sequence_name,start_value,increment increment_by,cycle_option cycle_flag" +
    " from information_schema.sequences where sequence_schema=':schema'"

}
