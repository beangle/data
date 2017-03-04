/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
import org.beangle.data.jdbc.dialect.OracleDialect
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.junit.runner.RunWith
import org.beangle.data.jdbc.dialect.PostgreSQLDialect
import org.beangle.commons.jdbc.ForeignKey
import org.beangle.commons.jdbc.Database
import org.beangle.commons.jdbc.Table
import org.beangle.commons.jdbc.Identifier
import org.beangle.commons.jdbc.PrimaryKey
import org.beangle.commons.jdbc.Engines
import org.beangle.data.jdbc.dialect.SQL

@RunWith(classOf[JUnitRunner])
class ForeignKeyTest extends FlatSpec with Matchers {

  "fk alter sql" should "corret" in {
    val tableA = buildTable()
    val fk = tableA.foreignKeys.head
    SQL.alterTableAddforeignKey(fk, new OracleDialect)
  }

  "drop table " should "corret" in {
    val tableA = buildTable()
    val pgdialect = new PostgreSQLDialect()
    tableA.attach(pgdialect.engine)
    tableA.schema.name = Identifier("lowercase_a")
    println(pgdialect.tableGrammar.dropCascade(tableA.qualifiedName))
    val fk = tableA.foreignKeys.head
    //assert(fk.alterSql == "alter table lowercase_a.\"SYS_TABLEA\" add constraInt \"FKXYZ\" foreign key (\"FKID\") references \"PUBLIC\".\"SYS_TABLE\" (\"ID\")")
  }

  "toLowerCase " should "correct" in {
    val database = new Database(Engines.PostgreSQL)
    val schema = database.getOrCreateSchema("public")
    val tableA = buildTable.clone(schema)
    val pgdialect = new PostgreSQLDialect()
    tableA.toCase(true)
    assert(tableA.foreignKeys.size == 1)
    val head = tableA.foreignKeys.head
    assert(head.name.value == "fkxyz")
    assert(head.columns.size == 1)
    assert(head.columns.head.value == "fkid")

    assert(head.referencedTable.schema.name.value == "public")
    assert(head.referencedTable.name.value == "sys_table")

    head.referencedTable.name = Identifier(head.referencedTable.name.value, true)
    tableA.attach(pgdialect.engine)

    assert(head.name.value == "fkxyz")
    assert(head.columns.size == 1)
    assert(head.columns.head.value == "fkid")
    assert(!head.referencedTable.name.quoted)
  }

  def buildTable(): Table = {
    val dialect = new OracleDialect()
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
