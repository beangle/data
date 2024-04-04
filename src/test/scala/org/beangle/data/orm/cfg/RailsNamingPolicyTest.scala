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

package org.beangle.data.orm.cfg

import org.beangle.commons.config.Resources
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.orm.cfg.Profiles
import org.beangle.data.orm.model.code.{NationBean, SchoolBean}
import org.beangle.data.orm.model.IdType
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class RailsNamingPolicyTest extends AnyFunSpec with Matchers {

  describe("RailsNamingPolicy") {
    it("Get Module") {
      System.setProperty("jpa_prefix", "public")
      val profiles = new Profiles(new Resources(None, ClassLoaders.getResources("META-INF/beangle/orm.xml"), None))
      val module = profiles.getProfile(classOf[NationBean])
      assert(module.schema.contains("public_code"))
      assert(profiles.getPrefix(classOf[NationBean]) == "gb_")

      val daoModule = profiles.getProfile(classOf[SchoolBean])
      assert(daoModule.parent.nonEmpty)
      assert(daoModule.parent.get.packageName == "org.beangle.data.orm.model")

      assert(profiles.getSchema(classOf[IdType]).contains("school"))
    }
  }
}
