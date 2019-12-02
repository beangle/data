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

import java.io.File

import org.beangle.commons.io.Files
import org.beangle.data.jdbc.engine.{Engines, PostgreSQL}
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SerializerTest extends AnyFunSpec with Matchers {

  describe("DBXML") {
    it("to xml") {
      val engine = Engines.PostgreSQL
      val db = new Database(engine)
      val security = db.getOrCreateSchema("TEST")

      val category = Table(security, "user-categories")
      category.addColumn("id", "integer")
      category.createPrimaryKey("id")
      security.addTable(category)

      val user = Table(security, "user")
      val column = user.addColumn("name", "varchar(30)")
      column.comment = Some("login  name")
      column.nullable = false

      user.addColumn("id", "bigint").nullable = false
      user.addColumn("enabled", "boolean").nullable = false
      user.addColumn("code", "varchar(20)").nullable = false

      user.addColumn("age", "integer").nullable = false
      user.addColumn("category_id", "integer")
      user.addColumn("\"key\"", "integer").comment = Some("""RSA key <expired="2019-09-09">""")

      user.createPrimaryKey("id")
      user.createForeignKey("category_id", category)
      user.createUniqueKey("\"key\"")
      user.createIndex(true, "code")
      security.addTable(user)
      val xml = Serializer.toXml(db)
      val file = File.createTempFile("database", ".xml")
      Files.writeString(file, xml)
      println("db xml is writed in " + file.getAbsolutePath)
      val db2 = Serializer.fromXml(xml)
      val xml2 = Serializer.toXml(db2)
      assert(xml == xml2)
    }
  }
}
