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
package org.beangle.data.hibernate.dao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.beangle.data.hibernate.naming.NationBean
import org.beangle.data.dao.OqlBuilder

@RunWith(classOf[JUnitRunner])
class OqlBuilderTest extends FunSpec with Matchers {

  describe("OqlBuilder") {
    it("GenStatement") {
      val builder = OqlBuilder.from(classOf[NationBean], "test")
      builder.groupBy("test.name").having("count(*)>1").select("test.name")

      val query1 = builder.build()
      assert(query1.statement == "select test.name from " + classOf[NationBean].getName + " test group by test.name having count(*)>1")

      val query2 = builder.orderBy("test.name").build()
      assert(query2.statement == "select test.name from " + classOf[NationBean].getName + " test group by test.name having count(*)>1 order by test.name")
    }
  }
}