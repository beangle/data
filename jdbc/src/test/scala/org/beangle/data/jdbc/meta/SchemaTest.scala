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

import org.beangle.data.jdbc.engine.Oracle10g
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SchemaTest extends AnyFunSpec with Matchers {
  val oracle = new Oracle10g
  describe("Schema") {
    it("getTable") {
      val database = new Database(oracle)
      val db = database.getOrCreateSchema("TEST")
      val table = db.createTable("\"t 1\"")
      db.tables.put(table.name, table)
      assert(db.getTable("test.\"t 1\"").isDefined)
      assert(db.getTable("\"t 1\"").isDefined)
      assert(db.getTable("Test.\"t 1\"").isDefined)
      assert(db.getTable("TEST.\"t 1\"").isDefined)
    }
    it("filter names") {
      val filter = new Schema.NameFilter()
      filter.include("*")
      filter.exclude("*{[0-9]+}")
      filter.exclude("*tmp*")
      filter.exclude("*bak*")
      assert(filter.isMatched("peoples"))
      assert(!filter.isMatched("people_2023"))
      val o = new Oracle10g
      val tables = List(o.toIdentifier("peoples"), o.toIdentifier("people_2023"),
        o.toIdentifier("people2332"), o.toIdentifier("people_bak"), o.toIdentifier("tmp_2023_grades"))
      val results = filter.filter(tables)
      assert(results.size == 1)
      assert(results.head.value == "PEOPLES")
    }
  }
}
