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

package org.beangle.data.hibernate

import org.beangle.commons.config.Resources
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.hibernate.model.IdType
import org.beangle.data.hibernate.naming.{NationBean, SchoolBean}
import org.beangle.data.orm.cfg.Profiles
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec

class GlobalSchemaTest extends AnyFunSpec with Matchers {

  describe("RailsNamingPolicy") {
    it("Get Module") {
      System.setProperty("beangle.data.orm.global_schema", "test")
      val profiles = new Profiles(new Resources(None, ClassLoaders.getResources("META-INF/beangle/orm.xml"), None))
      val module = profiles.getProfile(classOf[NationBean])
      assert(module.schema.contains("test"))
      assert(profiles.getPrefix(classOf[NationBean]) == "gb_")

      val daoModule = profiles.getProfile(classOf[SchoolBean])
      assert(daoModule.parent.nonEmpty)
      assert(daoModule.parent.get.packageName == "org.beangle.data.hibernate")

      assert(profiles.getSchema(classOf[IdType]).contains("test"))
      System.setProperty("beangle.data.orm.global_schema", "")
    }
  }
}
