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

class MySQL extends AbstractEngine("MySQL",Version("[5.0,)")) {
  override def quoteChars: (Char, Char) = {
    ('`', '`')
  }

  registerKeywords("index", "explain")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "longtext", LONGVARCHAR -> "longtext",
    BOOLEAN -> "bit", BIT -> "bit",
    TINYINT -> "tinyint", SMALLINT -> "smallint", INTEGER -> "integer", BIGINT -> "bigint",
    FLOAT -> "float", DOUBLE -> "double precision",
    DECIMAL -> "decimal($p,$s)", NUMERIC -> "decimal($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "datetime",
    BINARY -> "binary($l)", VARBINARY -> "longblob", LONGVARBINARY -> "longblob",
    BLOB -> "longblob", CLOB -> "longtext", NCLOB -> "longtext")

  registerTypes2(
    (VARCHAR, 65535, "varchar($l)"),
    (NUMERIC, 65, "decimal($p, $s)"),
    (NUMERIC, Int.MaxValue, "decimal(65, $s)"),
    (VARBINARY, 255, "tinyblob"),
    (VARBINARY, 65535, "blob"),
    (VARBINARY, 16777215, "mediumblob"),
    (LONGVARBINARY, 16777215, "mediumblob"))

  override def defaultSchema: String = {
    "PUBLIC"
  }

}
