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

package org.beangle.data.jdbc

import java.sql.Types

import org.beangle.commons.lang.annotation.value
import org.beangle.data.jdbc.engine.Engines
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec

class SqlTypeMappingTest extends AnyFunSpec with Matchers {
  describe("SqlTypeMapping") {
    it("test value type") {
      val mapping = new DefaultSqlTypeMapping(Engines.forName("h2"))
      assert(mapping.sqlCode(classOf[Terms]) == Types.SMALLINT)
      assert(mapping.sqlCode(classOf[Meta.V]) == Types.INTEGER)
      assert(mapping.sqlCode(classOf[Array[Byte]]) == Types.VARBINARY)
    }
  }
}

@value
class Terms(value: Short)

object Meta extends Enumeration(1) {
  val A = new V(1)
  val B = new V(2)
  val C = new V(3)
  class V(id:Int) extends Val(id)
}
