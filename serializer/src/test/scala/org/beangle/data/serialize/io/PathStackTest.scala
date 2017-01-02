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
package org.beangle.data.serialize.io

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PathStackTest extends FunSpec with Matchers {

  describe("PathStack") {
    it("push and pop") {
      val stack = new PathStack(16)
      stack.push("table",null)
      stack.push("tr",null)
      stack.push("td",null)
      stack.push("form",null)
      stack.pop()
      stack.pop()
      stack.push("td",null)
      stack.push("div",null)
      assert("table[1]/tr[1]/td[2]/div[1]" == stack.currentPath.explicit)
    }
  }
}