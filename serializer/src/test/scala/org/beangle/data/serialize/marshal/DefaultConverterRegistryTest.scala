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
package org.beangle.data.serialize.marshal

import java.{ util => ju }
import org.beangle.data.serialize.mapper.DefaultMapper
import org.beangle.data.serialize.model.Skill
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.collection.page.SinglePage

@RunWith(classOf[JUnitRunner])
class DefaultMarshallerRegistryTest extends FunSpec with Matchers {

  val registry = new DefaultMarshallerRegistry(new DefaultMapper)

  describe("DefaultMarshallerRegistry") {
    it("lookup marshaller ") {
      val skills = Array(new Skill("Play Basketball Best"), new Skill("Play football"))
      val converter = registry.lookup(skills.getClass)
      val converter2 = registry.lookup(skills.getClass)
      assert(converter != null)
      assert(converter.support(skills.getClass))
      assert(converter.getClass.getName == "org.beangle.data.serialize.marshal.ArrayMarshaller")

      val dateMarshaller = registry.lookup(classOf[ju.Date])
      assert(dateMarshaller != null)
      assert(dateMarshaller.isInstanceOf[DateMarshaller])
    }
    it("lookup page marshaller") {
      val marshaller = registry.lookup(classOf[SinglePage[_]])
      assert(null != marshaller)
      assert(marshaller.targetType == Type.Object)
    }
  }
}