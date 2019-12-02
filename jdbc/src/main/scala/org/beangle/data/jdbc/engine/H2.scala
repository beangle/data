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

class H2 extends AbstractEngine("H2", Version("[1.3,)")) {
  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar($l)", LONGVARCHAR -> "longvarchar",
    BOOLEAN -> "boolean", BIT -> "bit",
    TINYINT -> "tinyint", SMALLINT -> "smallint", INTEGER -> "integer", BIGINT -> "bigint",
    FLOAT -> "float", DOUBLE -> "double",
    DECIMAL -> "decimal", NUMERIC -> "numeric",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "timestamp",
    BINARY -> "binary", VARBINARY -> "varbinary($l)", LONGVARBINARY -> "longvarbinary",
    BLOB -> "longvarbinary", CLOB -> "longvarchar")

  override def storeCase: StoreCase.Value = {
    StoreCase.Upper
  }

  override def defaultSchema: String = {
    "PUBLIC"
  }
  metadataLoadSql.sequenceSql = "select sequence_name,current_value,increment,cache from information_schema.sequences where sequence_schema=':schema'"
}