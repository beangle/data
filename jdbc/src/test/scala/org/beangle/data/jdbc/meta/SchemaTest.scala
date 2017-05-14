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

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSpec
import org.beangle.data.jdbc.dialect.OracleDialect
import org.beangle.commons.jdbc.Engines
import org.beangle.commons.jdbc.Database
import org.beangle.commons.jdbc.Table

@RunWith(classOf[JUnitRunner])
class SchemaTest extends FunSpec with Matchers {

  describe("Schema") {
    it("getTable") {
      val dialect = new OracleDialect
      val database = new Database(Engines.Oracle)
      val db = database.getOrCreateSchema("TEST")
      val table = new Table(db, "t 1")
      db.tables.put(table.name, table)
      assert(db.getTable("test.\"t 1\"").isDefined)
      assert(db.getTable("\"t 1\"").isDefined)
      assert(db.getTable("Test.\"t 1\"").isDefined)
      assert(db.getTable("TEST.\"t 1\"").isDefined)
    }
  }
}
