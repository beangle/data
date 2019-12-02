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

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.dialect._

object EngineTest {

  def main(args: Array[String]): Unit = {
    printTypeMatrix()
  }

  private def printPad(name: String): Unit = {
    print(Strings.rightPad(name, 26, ' '))
  }

  def printTypeMatrix(): Unit = {
    import java.sql.Types._
    val types = Array(BOOLEAN, BIT, CHAR, TINYINT, SMALLINT, INTEGER, BIGINT,
      FLOAT, REAL, DOUBLE, DECIMAL, NUMERIC, DATE, TIME, TIMESTAMP, VARCHAR, LONGVARCHAR,
      BINARY, VARBINARY, LONGVARBINARY, BLOB, CLOB)

    val typeNames = Array("BOOLEAN", "BIT", "CHAR", "TINYINT", "SMALLINT", "INTEGER", "BIGINT",
      "FLOAT", "REAL", "DOUBLE", "DECIMAL", "NUMERIC", "DATE", "TIME", "TIMESTAMP", "VARCHAR", "LONGVARCHAR",
      "BINARY", "VARBINARY", "LONGVARBINARY", "BLOB", "CLOB")

    val dialects = Array(new OracleDialect, new PostgreSQLDialect, new MySQLDialect,
      new SQLServerDialect, new DB2Dialect, new H2Dialect)

    printPad("Type/DBEngine")
    for (dialect <- dialects) {
      printPad(Strings.replace(dialect.getClass.getSimpleName, "Dialect", ""))
    }

    println()
    for (i <- types.indices) {
      printPad(typeNames(i))
      for (dialect <- dialects) {
        val typeName =
          try {
            dialect.engine.toType(types(i)).name
          } catch {
            case e: Exception => "error"
          }
        printPad(typeName)
      }
      println("")
    }
  }
}
