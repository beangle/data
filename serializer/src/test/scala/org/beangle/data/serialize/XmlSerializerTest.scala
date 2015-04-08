/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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

import java.io.File
import org.beangle.data.serialize.io.xml.DomDriver
import org.beangle.data.serialize.model.{ Address, Person }
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.collection.page.SinglePage

@RunWith(classOf[JUnitRunner])
class XmlSerializerTest extends FunSpec with Matchers {

  describe("XmlSerializer") {
    it("serializer xml ") {
      val serializer = XmlSerializer()
      serializer.alias("person", classOf[Person])
      serializer.alias("address", classOf[Address])
      serializer.alias("list", classOf[::[_]])
      //println(serializer.serialize(List(Some(new Person("002", "admin2")), new Person("001", "admin"))))
      //println(serializer.serialize("3"))
      //println(serializer.serialize(new SinglePage(1, 2, 200, List(new Person("002", "admin2"), new Person("001", "admin")))))
    }
  }
}
