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

class DB2 extends AbstractEngine("DB2", Version("[8.0]")) {
  metadataLoadSql.sequenceSql = "select name as sequence_name,start-1 as current_value,increment,cache from sysibm.syssequences where schema=':schema'"

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

  override def defaultSchema: String = null
}
