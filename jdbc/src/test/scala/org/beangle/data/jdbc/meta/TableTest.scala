/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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

  val dialect =

    "create sql" should "like this" in {
      val table = new Table("TEST", "USER")
      val column = new Column("NAME", Types.VARCHAR)
      column.comment = "login name"
      column.size = 30
      table.add(column)
      val pkColumn = new Column("ID", Types.DECIMAL)
      pkColumn.size = 19
      val pk = new PrimaryKey(table, "pk", "ID")
      table.add(pkColumn)
      table.primaryKey = pk
      val boolCol = new Column("ENABLED", Types.DECIMAL)
      boolCol.size = 1
      table.add(boolCol)

      val ageCol = new Column("AGE", Types.DECIMAL)
      ageCol.size = 10
      table.add(ageCol)

      table.attach(new PostgreSQLDialect())
      assert("create table \"TEST\".\"USER\" (\"NAME\" varchar(30) not null, \"ID\" int8 not null, \"ENABLED\" boolean not null, \"AGE\" int4 not null," +
        " primary key (\"ID\"))" == table.createSql)
    }

  "lowercase " should "corrent" in {
    val table = new Table("PUBLIC", "USER")
    val cloned = table.clone(new PostgreSQLDialect())
    (cloned == table) should be(false)
    cloned.toCase(true)
    table.name.value should equal("USER")
    cloned.name.value should equal("user")
  }
}
