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
package org.beangle.data.model.util;

import org.beangle.data.model.meta.{ BasicType, Domain }
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PopulatorTest extends FunSpec with Matchers {

  describe("Populator") {
    it("populate error attr") {
      val populator = new ConvertPopulator()
      val menu = new Menu
      val menuET = new Domain.EntityTypeImpl(classOf[Menu])
      menuET.properties += ("id" -> new Domain.SingularPropertyImpl("id", classOf[Int], new BasicType(classOf[Int])))
      populator.populate(menu, menuET, "aa", "xx") should be(false)
      populator.populate(menu, menuET, "id", "2") should be(true)
      populator.populate(menu, menuET, "id", "0") should be(true)
    }
  }
}
