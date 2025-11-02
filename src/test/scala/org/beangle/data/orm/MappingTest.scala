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

package org.beangle.data.orm

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.orm.model.UserProperty
import org.beangle.jdbc.engine.{Engines, PostgreSQL10}
import org.beangle.jdbc.meta.Database
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/**
 * @author chaostone
 */
class MappingTest extends AnyFunSpec with Matchers {
  describe("Mapping") {
    it("bind") {
      val engine = new PostgreSQL10
      val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle.xml")
      val mappings = new Mappings(new Database(engine), ormLocations)
      mappings.locale = java.util.Locale.SIMPLIFIED_CHINESE
      mappings.autobind()
      val menuBeanInfo = BeanInfos.get(classOf[UserProperty])
      assert(menuBeanInfo.properties("id").typeinfo.clazz == classOf[Long])
    }
  }
}
