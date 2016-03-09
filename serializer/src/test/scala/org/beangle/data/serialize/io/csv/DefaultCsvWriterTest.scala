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
package org.beangle.data.serialize.io.csv

import java.io.FileOutputStream
import java.util.Date
import org.beangle.data.serialize.{ AbstractSerializer, CsvSerializer }
import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.data.serialize.model.{ Address, Person, Skill }
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DefaultCsvWriterTest extends FunSpec with Matchers {

  describe("DefaultCsvWriter") {
    it("getAttributes") {
      val serializer = CsvSerializer().asInstanceOf[AbstractSerializer]
      val os = new FileOutputStream("/tmp/a.csv")
      val params = Map("properties" -> List(
        classOf[Person] -> List("code", "name", "accountMoney1", "bestSkill", "skills", "families", "sidekick", "address"),
        classOf[Skill] -> List("name")))
      val writer = serializer.driver.createWriter(os, params).asInstanceOf[DefaultCsvWriter]
      val context = new MarshallingContext(serializer, writer, serializer.registry, params)

      val personProperties = context.getProperties(classOf[Person])
      assert(personProperties != null)
      assert(personProperties.size == 6)
      assert(context.getProperties(classOf[java.util.Date]) == List())
      assert(context.getProperties(classOf[Address]) != null)
      assert(context.getProperties(classOf[Address]).size == 3)

      var csvProperties = writer.getProperties(context)
      assert(csvProperties.length == 8)
    }
  }
}
