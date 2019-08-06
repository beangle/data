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
package org.beangle.data.orm

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.jdbc.meta.{Database, Engines}
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

/**
  * @author chaostone
  */
@RunWith(classOf[JUnitRunner])
class MappingTest extends AnyFunSpec with Matchers {
  describe("Mapping") {
    it("bind") {
      val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle/orm.xml")
      val mappings = new Mappings(new Database(Engines.PostgreSQL), ormLocations)
      mappings.locale = java.util.Locale.SIMPLIFIED_CHINESE
      mappings.autobind()
      val menuBeanInfo = BeanInfos.Default.get(classOf[UserProperty])
      assert(menuBeanInfo.properties("id").typeinfo.clazz == classOf[Long])
    }
  }
}
