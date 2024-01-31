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

package org.beangle.data.jdbc.meta

import org.beangle.data.jdbc.engine.{Oracle10g, PostgreSQL10}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.sql.Types

class TableTest extends AnyFlatSpec with Matchers {

  val oracle = new Oracle10g
  val postgresql = new PostgreSQL10
  val oracleDb = new Database(oracle)
  val test = oracleDb.getOrCreateSchema("TEST")
  val public = oracleDb.getOrCreateSchema("PUBLIC")

  "create sql" should "like this" in {
    val table = new Table(test, Identifier("user"))
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

    table.attach(postgresql)
    println(postgresql.createTable(table))
    assert("create table TEST.\"user\" (name varchar(30) not null," +
      " id bigint not null, enabled boolean not null, age integer not null)" == postgresql.createTable(table))
  }

  "postgresql " should " attach to oracle" in {
    val pgDb = new Database(postgresql)
    val public = pgDb.getOrCreateSchema("public")
    val user = new Table(public, Identifier("USERS"))
    val active = new Column("active", postgresql.toType(Types.BOOLEAN))
    active.defaultValue = Some("false")
    user.add(active)

    assert(oracle.createTable(user.attach(oracle)) == """create table public.USERS (ACTIVE number(1,0) default 0)""")
  }

  "lowercase " should "correct" in {
    val table = new Table(public, Identifier("USER"))
    val database = new Database(postgresql)
    val test = database.getOrCreateSchema("TEST")

    val cloned = table.clone(test)
    (cloned == table) should be(false)
    cloned.toCase(true)
    table.name.value should equal("USER")
    cloned.name.value should equal("user")
  }
}
