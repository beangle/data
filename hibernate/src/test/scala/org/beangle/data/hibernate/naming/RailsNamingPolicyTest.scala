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
package org.beangle.data.hibernate.naming

import org.beangle.commons.config.Resources
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.hibernate.model.IdType
import org.beangle.data.orm.cfg.Profiles
import org.junit.runner.RunWith
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RailsNamingPolicyTest extends AnyFunSpec with Matchers {

  describe("RailsNamingPolicy") {
    it("Get Module") {
      System.setProperty("jpa_prefix", "public")
      val profiles = new Profiles(new Resources(None, ClassLoaders.getResources("META-INF/beangle/orm.xml"), None))
      val module = profiles.getProfile(classOf[NationBean])
      assert(module.schema.contains("public_naming"))
      assert(profiles.getPrefix(classOf[NationBean]) == "gb_")

      val daoModule = profiles.getProfile(classOf[SchoolBean])
      assert(daoModule.parent.packageName == "org.beangle.data.hibernate")

      assert(profiles.getSchema(classOf[IdType]).contains("school"))
    }
  }
}
