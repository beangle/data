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

class PostgreSQL extends AbstractEngine("PostgreSQL", Version("[8.4)")) {
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

  metadataLoadSql.sequenceSql = "select sequence_name,start_value,increment increment_by,cycle_option cycle_flag" +
    " from information_schema.sequences where sequence_schema=':schema'"

}