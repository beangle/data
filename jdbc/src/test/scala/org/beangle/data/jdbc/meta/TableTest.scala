/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.meta

import java.sql.Types
import org.beangle.data.jdbc.dialect.H2Dialect
import org.beangle.data.jdbc.dialect.PostgreSQLDialect
import org.junit.runner.RunWith
import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.beangle.data.jdbc.dialect.OracleDialect

@RunWith(classOf[JUnitRunner])
class TableTest extends FlatSpec with Matchers {

  val dialect = new PostgreSQLDialect()

  "create sql" should "like this" in {
    val table = new Table("test", "user")
    val column = new Column("name", Types.VARCHAR)
    column.comment = "login name"
    column.size = 30
    table.add(column)
    val pkColumn = new Column("id", Types.DECIMAL)
    pkColumn.size = 19
    val pk = new PrimaryKey("pk", "id")
    table.add(pkColumn)
    table.primaryKey = pk
    val boolCol = new Column("enabled", Types.DECIMAL)
    boolCol.size = 1
    table.add(boolCol)

    val ageCol = new Column("enabled", Types.DECIMAL)
    ageCol.size = 10
    table.add(ageCol)

    println(table.createSql(new H2Dialect()))
    println(table.createSql(dialect))
    table.attach(dialect)
    println(table.createSql(new OracleDialect))
  }

  "lowercase " should "corrent" in {
    val table = new Table("PUBLIC", "USER")
    val cloned = table.clone(dialect)
    (cloned == table) should be(false)
    cloned.lowerCase
    "USER".equals(table.name) should be(true)
    "user".equals(cloned.name) should be(true)
  }
}
