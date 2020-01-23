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
package org.beangle.data.dao

import org.junit.runner.RunWith
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConditionTest extends AnyFunSpec with Matchers {

  describe("Condition") {
    it("paramNames should given list string") {
      val con = new Condition("a.id=:id and b.name=:name")
      val paramNames = con.paramNames
      assert(null != paramNames)
      assert(paramNames.size == 2)
      assert(paramNames.contains("id"))
      assert(paramNames.contains("name"))

      val con2 = new Condition(":beginOn < a.beginOn and b.name=:name")
      val paramNames2 = con2.paramNames
      assert(null != paramNames)
      assert(paramNames2.size == 2)
      assert(paramNames2.contains("beginOn"))
      assert(paramNames2.contains("name"))
    }
  }
}
