/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.jpa.mapping
import org.scalatest.FunSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.beangle.commons.bean.PropertyUtils
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.jpa.dao.TestDaoBean

@RunWith(classOf[JUnitRunner])
class RailsNamingPolicyTest extends FunSpec with Matchers {

  describe("RailsNamingPolicy") {
    it("Get Module") {
      val policy = new RailsNamingPolicy
      for (resource <- ClassLoaders.getResources("META-INF/beangle/table.xml"))
        policy.addConfig(resource)
      assert(policy.getSchema("org.beangle.data.jpa.sub").isDefined)
      val module = policy.getModule(classOf[TestBean])
      assert(module.isDefined)
      assert(module.get.schema == Some("mapping"))
      assert(policy.getPrefix(classOf[TestBean]) == "gb_")

      val daoModule = policy.getModule(classOf[TestDaoBean])
      assert(daoModule.isDefined)
      assert(daoModule.get.parent.packageName == "org.beangle.data.jpa")
    }
  }
}