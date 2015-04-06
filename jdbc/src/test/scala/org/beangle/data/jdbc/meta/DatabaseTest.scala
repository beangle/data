package org.beangle.data.jdbc.meta

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSpec
import org.beangle.data.jdbc.dialect.OracleDialect
import org.beangle.data.jdbc.dialect.Name

@RunWith(classOf[JUnitRunner])
class DatabaseTest extends FunSpec with Matchers {

  describe("Database") {
    it("getTable") {
      val dialect = new OracleDialect
      val db = new Database(null, dialect, null, Name("TEST"))
      val table = new Table("TEST", "t 1")
      db.tables.put(table.id, table)
      assert(db.getTable("test.\"t 1\"").isDefined)
      assert(db.getTable("\"t 1\"").isDefined)
      assert(db.getTable("Test.\"t 1\"").isDefined)
      assert(db.getTable("TEST.\"t 1\"").isDefined)
    }
  }
}