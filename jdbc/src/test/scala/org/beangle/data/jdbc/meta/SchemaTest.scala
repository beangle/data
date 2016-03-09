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

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSpec
import org.beangle.data.jdbc.dialect.OracleDialect
import org.beangle.data.jdbc.dialect.Name

@RunWith(classOf[JUnitRunner])
class SchemaTest extends FunSpec with Matchers {

  describe("Schema") {
    it("getTable") {
      val dialect = new OracleDialect
      val db = new Schema(dialect, null, Name("TEST"))
      val table = new Table("TEST", "t 1")
      db.tables.put(table.name.value, table)
      assert(db.getTable("test.\"t 1\"").isDefined)
      assert(db.getTable("\"t 1\"").isDefined)
      assert(db.getTable("Test.\"t 1\"").isDefined)
      assert(db.getTable("TEST.\"t 1\"").isDefined)
    }
  }
}