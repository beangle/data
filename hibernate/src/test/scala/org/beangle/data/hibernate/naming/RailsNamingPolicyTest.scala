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
package org.beangle.data.hibernate.naming

import org.beangle.commons.config.Resources
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.orm.cfg.Profiles
import org.beangle.data.hibernate.model.IdType
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RailsNamingPolicyTest extends FunSpec with Matchers {

  describe("RailsNamingPolicy") {
    it("Get Module") {
      System.setProperty("jpa_prefix", "j_")
      val profiles = new Profiles(new Resources(None, ClassLoaders.getResources("META-INF/beangle/orm.xml"), None))
      val module = profiles.getProfile(classOf[NationBean])
      assert(module.isDefined)
      assert(module.get.schema == Some("j_naming"))
      assert(profiles.getPrefix(classOf[NationBean]) == "gb_")

      val daoModule = profiles.getProfile(classOf[SchoolBean])
      assert(daoModule.isDefined)
      assert(daoModule.get.parent.packageName == "org.beangle.data.hibernate")

      assert(profiles.getSchema(classOf[IdType]) == Some("school"))
    }
  }
}
