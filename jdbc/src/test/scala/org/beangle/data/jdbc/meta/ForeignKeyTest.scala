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

import org.beangle.data.jdbc.engine.Engines
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ForeignKeyTest extends AnyFlatSpec with Matchers {

  "fk alter sql" should "corret" in {
    val tableA = buildTable()
    val fk = tableA.foreignKeys.head
    Engines.Oracle.alterTableAddForeignKey(fk)
  }

  "drop table " should "corret" in {
    val tableA = buildTable()
    val pgdialect = Engines.PostgreSQL
    tableA.attach(pgdialect)
    tableA.schema.name = Identifier("lowercase_a")
    println(pgdialect.dropTable(tableA.qualifiedName))
    val fk = tableA.foreignKeys.head
    //assert(fk.alterSql == "alter table lowercase_a.\"SYS_TABLEA\" add constraInt \"FKXYZ\" foreign key (\"FKID\") references \"PUBLIC\".\"SYS_TABLE\" (\"ID\")")
  }

  "toLowerCase " should "correct" in {
    val database = new Database(Engines.PostgreSQL)
    val schema = database.getOrCreateSchema("public")
    val tableA = buildTable.clone(schema)
    val pgdialect = Engines.PostgreSQL
    tableA.toCase(true)
    assert(tableA.foreignKeys.size == 1)
    val head = tableA.foreignKeys.head
    assert(head.name.value == "fkxyz")
    assert(head.columns.size == 1)
    assert(head.columns.head.value == "fkid")

    assert(head.referencedTable.schema.name.value == "public")
    assert(head.referencedTable.name.value == "sys_table")

    head.referencedTable.name = Identifier(head.referencedTable.name.value, true)
    tableA.attach(pgdialect)

    assert(head.name.value == "fkxyz")
    assert(head.columns.size == 1)
    assert(head.columns.head.value == "fkid")
    assert(!head.referencedTable.name.quoted)
  }

  def buildTable(): Table = {
    val database = new Database(Engines.Oracle)
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
