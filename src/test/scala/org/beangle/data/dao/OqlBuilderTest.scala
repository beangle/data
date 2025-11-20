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

package org.beangle.data.dao

import org.beangle.data.orm.model.TestUser
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class OqlBuilderTest extends AnyFunSpec, Matchers {

  describe("OqlBuilder") {
    it("builder") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      import q.given
      q.where { e =>
        (e.member.middleName.isNotNull or e.friends.isNotNull or e.properties.isNotNull)
          .and(e.birthday.isNull)
          .and(e.updatedAt equal Instant.now)
          .and(e.role.name like "test")
          .and(e.id gt 2)
      }
      q.on { e =>
        q.groupBy(e.id, e.role.name)
          .select(e.id, e.role.name, "count(*)")
          .orderBy(e.role.name)
      }

      val query = q.build()
      assert(query.params.size == 3)
      assert(query.params.contains("v1"))
      assert(query.params.contains("v2"))
      assert(query.params.contains("v3"))
      assert(query.statement == "select t.id,t.role.name,count(*) from org.beangle.data.orm.model.TestUser t" +
        " where (t.member.middleName is not null or t.friends is not null or t.properties is not null)" +
        " and t.birthday is null and t.updatedAt = :v1 and t.role.name like :v2 and t.id > :v3" +
        " group by t.id,t.role.name order by t.role.name")
    }

    it("user define function") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      import q.given
      q.where { e =>
        e.member.middleName is("len(_)=?", 2) or
          e.id.is("bitand(_,?)>0", 123)
      }
      val query = q.build()
      assert(query.statement == "select t from org.beangle.data.orm.model.TestUser t" +
        " where len(t.member.middleName)=:v1 or bitand(t.id,:v2)>0")
    }
  }

}
