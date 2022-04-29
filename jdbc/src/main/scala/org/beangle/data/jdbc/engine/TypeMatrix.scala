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

import org.beangle.commons.lang.Strings

object TypeMatrix {

  def main(args: Array[String]): Unit = {
    printTypeMatrix()
  }

  private def leftPad(name: String): Unit = {
    print(Strings.leftPad(name, 15, ' ') +"    ")
  }

  private def rightPad(name: String): Unit = {
    val info =
      if name.length > 15 then name.substring(0, 12) + "..."
      else Strings.rightPad(name, 15, ' ')
    print(info)
  }

  def printTypeMatrix(): Unit = {
    import java.sql.Types.*
    val types = Array(BOOLEAN, BIT, CHAR, NCHAR, TINYINT, SMALLINT, INTEGER, BIGINT,
      FLOAT, REAL, DOUBLE, DECIMAL, NUMERIC, DATE, TIME, TIMESTAMP, VARCHAR, NVARCHAR, LONGVARCHAR, LONGNVARCHAR,
      BINARY, VARBINARY, LONGVARBINARY, BLOB, CLOB, NCLOB)

    val typeNames = Array("BOOLEAN", "BIT", "CHAR", "NCHAR", "TINYINT", "SMALLINT", "INTEGER", "BIGINT",
      "FLOAT", "REAL", "DOUBLE", "DECIMAL", "NUMERIC", "DATE", "TIME", "TIMESTAMP", "VARCHAR", "NVARCHAR",
      "LONGVARCHAR", "LONGNVARCHAR", "BINARY", "VARBINARY", "LONGVARBINARY", "BLOB", "CLOB", "NCLOB")

    val engines = Array(new PostgreSQL10, new H2, new MySQL5,
      new Oracle10g, new SQLServer2008, new DB2V8)

    leftPad("Type/DBEngine")
    for (dialect <- engines) {
      rightPad(Strings.replace(dialect.getClass.getSimpleName, "Dialect", ""))
    }

    println("-" * 15 * 7)
    for (i <- types.indices) {
      leftPad(typeNames(i))
      for (engine <- engines) {
        val typeName =
          try {
            engine.toType(types(i)).name
          } catch {
            case _: Exception => "error"
          }
        rightPad(typeName)
      }
      println("")
    }
  }
}
