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
package org.beangle.data.jdbc.meta

import java.sql.Types

import org.beangle.data.jdbc.dialect.{ PostgreSQLDialect, SQL }
import org.junit.runner.RunWith
import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TableTest extends FlatSpec with Matchers {

  val oracle = Engines.Oracle
  val oracleDb = new Database(oracle)
  val test = oracleDb.getOrCreateSchema("TEST")
  val public = oracleDb.getOrCreateSchema("PUBLIC")

  "create sql" should "like this" in {
    val table = new Table(test, Identifier("USER"))
    val column = new Column("NAME", oracle.toType(Types.VARCHAR, 30))
    column.comment = Some("login name")
    column.nullable = false
    table.add(column)
    val pkColumn = new Column("ID", oracle.toType(Types.DECIMAL, 19))
    pkColumn.nullable = false
    val pk = new PrimaryKey(table, "pk", "ID")
    table.add(pkColumn)
    table.primaryKey = Some(pk)
    val boolCol = new Column("ENABLED", oracle.toType(Types.DECIMAL, 1))
    boolCol.nullable = false
    table.add(boolCol)

    val ageCol = new Column("AGE", oracle.toType(Types.DECIMAL, 10))
    ageCol.nullable = false
    table.add(ageCol)

    table.attach(Engines.PostgreSQL)
    assert("create table TEST.\"USER\" (\"NAME\" varchar(30) not null, \"ID\" int8 not null, \"ENABLED\" boolean not null, \"AGE\" int4 not null," +
      " primary key (\"ID\"))" == SQL.createTable(table, new PostgreSQLDialect()))
  }

  "lowercase " should "corrent" in {
    val table = new Table(public, Identifier("USER"))
    val database = new Database(Engines.PostgreSQL)
    val test = database.getOrCreateSchema("TEST")

    val cloned = table.clone(test)
    (cloned == table) should be(false)
    cloned.toCase(true)
    table.name.value should equal("USER")
    cloned.name.value should equal("user")
  }
}
