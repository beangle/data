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

import org.beangle.commons.io.Files
import org.beangle.data.jdbc.engine.PostgreSQL10
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

class SerializerTest extends AnyFunSpec with Matchers {

  describe("DBXML") {
    it("to xml") {
      val engine = new PostgreSQL10
      val db = new Database(engine)
      val security = db.getOrCreateSchema("TEST")

      val category = Table(security, "user-categories")
      category.createColumn("id", "integer")
      category.createPrimaryKey("", "id")
      security.addTable(category)

      val user = Table(security, "users")
      val column = user.createColumn("name", "varchar(30)")
      column.comment = Some("login  name")
      column.nullable = false

      user.createColumn("id", "bigint").nullable = false
      user.createColumn("enabled", "boolean").nullable = false
      user.createColumn("code", "varchar(20)").nullable = false

      user.createColumn("age", "integer").nullable = false
      user.createColumn("category_id", "integer")
      user.createColumn("\"key\"", "integer").comment = Some("""RSA key <expired="2019-09-09">""")

      user.createPrimaryKey(null, "id")
      user.createForeignKey(null, "category_id", category)
      user.createUniqueKey(null, "\"key\"")
      user.createIndex(null, true, "code")
      user.convertIndexToUniqueKeys()
      security.addTable(user)

      val account = security.createView("accounts")
      account.createColumn("id", "bigint")
      account.createColumn("code", "varchar(20)")
      account.createColumn("enabled", "boolean")
      account.definition = Some("select id,code,enabled from security.users")

      val xml = Serializer.toXml(db)
      val file = File.createTempFile("database", ".xml")
      Files.writeString(file, xml)
      println("db xml is written in " + file.getAbsolutePath)
      val db2 = Serializer.fromXml(xml)
      val xml2 = Serializer.toXml(db2)
      assert(xml == xml2)
    }
  }
}
