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

import org.beangle.data.jdbc.engine.{Engines, Oracle10g, PostgreSQL10}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ForeignKeyTest extends AnyFlatSpec with Matchers {

  val oracle = new Oracle10g
  val postgresql = new PostgreSQL10

  "fk alter sql" should "corret" in {
    val tableA = buildTable()
    val fk = tableA.foreignKeys.head
    oracle.alterTableAddForeignKey(fk)
  }

  "drop table " should "corret" in {
    val tableA = buildTable()
    tableA.attach(postgresql)
    tableA.schema.name = Identifier("lowercase_a")
    println(postgresql.dropTable(tableA.qualifiedName))
    val fk = tableA.foreignKeys.head
  }

  "toLowerCase " should "correct" in {
    val database = new Database(postgresql)
    val schema = database.getOrCreateSchema("public")
    val tableA = buildTable().clone(schema)
    tableA.toCase(true)
    assert(tableA.foreignKeys.size == 1)
    val head = tableA.foreignKeys.head
    assert(head.name.value == "fkxyz")
    assert(head.columns.size == 1)
    assert(head.columns.head.value == "fkid")

    assert(head.referencedTable.schema.name.value == "public")
    assert(head.referencedTable.name.value == "sys_table")

    head.referencedTable.name = Identifier(head.referencedTable.name.value, true)
    tableA.attach(postgresql)

    assert(head.name.value == "fkxyz")
    assert(head.columns.size == 1)
    assert(head.columns.head.value == "fkid")
    assert(!head.referencedTable.name.quoted)
  }

  def buildTable(): Table = {
    val database = new Database(oracle)
    val schema = database.getOrCreateSchema("public")
    val table = new Table(schema, Identifier("SYS_TABLE"))
    val pk = new PrimaryKey(table, "PK", "ID")
    table.primaryKey = Some(pk)

    val tableA = new Table(schema, Identifier("SYS_TABLEA"))
    val fk = new ForeignKey(tableA, Identifier("FKXYZ"), Identifier("FKID"))
    tableA.add(fk)
    fk.refer(table, Identifier("ID"))
    tableA
  }

}
