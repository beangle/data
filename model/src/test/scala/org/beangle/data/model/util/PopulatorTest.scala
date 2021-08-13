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

package org.beangle.data.model.util

import org.beangle.commons.lang.reflect.{BeanInfo, BeanInfos}
import org.beangle.data.model.meta.Domain
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PopulatorTest extends AnyFunSpec with Matchers {

  describe("Populator") {
    it("populate error attr") {

      val menuBeanInfo = BeanInfos.load(classOf[Menu])
      val populator = new ConvertPopulator()
      val menu = new Menu
      val menuET = new SimpleEntityType(classOf[Menu])
      menuET.properties += ("id" -> new Domain.SimpleProperty("id", classOf[Int], true))
      populator.populate(menu, menuET, "aa", "xx") should be(false)
      populator.populate(menu, menuET, "id", "3") should be(true)
      val id = menu.id
      populator.populate(menu, menuET, "id", "0") should be(true)
    }
  }

}
