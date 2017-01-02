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
package org.beangle.data.serialize

import org.beangle.data.serialize.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.collection.page.SinglePage

@RunWith(classOf[JUnitRunner])
class CsvSerializerTest extends FunSpec with Matchers {

  describe("CsvSerializer") {
    it("serializer csv ") {
      val csv = CsvSerializer()
      val params = Map("properties" -> List(
        classOf[Person] -> List("code", "address", "sidekick", "skills"),
        classOf[Address] -> List("street", "name")))
      //println(csv.serialize(List(new Person("002", "admin2"), new Person("001", "admin")), params))
    }
    it("serializer page ignore collection") {
      val csv = CsvSerializer()
      val params = Map("properties" -> List(
        classOf[Person] -> List("code", "address", "sidekick", "skills"),
        classOf[Address] -> List("street", "name")))
      //println(csv.serialize(new SinglePage(1, 2, 100, List(new Person("002", "admin2"), new Person("001", "admin"))), params))
    }
  }
}
