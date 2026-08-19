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

import org.beangle.data.dao.OqlBuilder.*
import org.beangle.data.orm.model.{TestRole, TestUser}
import org.beangle.data.orm.model.code.NationBean
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class OqlBuilderTest extends AnyFunSpec, Matchers {

  describe("OqlBuilder") {
    it("builder") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e =>
        (e.member.middleName.isNotNull or e.friends.isNotNull or e.properties.isNotNull)
          .and(e.birthday.isNull)
          .and(e.updatedAt equal Instant.now)
          .and(e.role.name like "test")
          .and(e.id gt 2)
      }
      q.on { e =>
        q.groupBy(e.id, e.role.name)
          .select(e.id, e.role.name, "count(*)", max(e.id), count(distinct(e.role.name)), e.id.f("avg(_)"))
          .orderBy(e.role.name)
      }

      val query = q.build()
      assert(query.params.size == 3)
      assert(query.params.contains("v1"))
      assert(query.params.contains("v2"))
      assert(query.params.contains("v3"))
      assert(query.statement == "select t.id,t.role.name,count(*),max(t.id),count(distinct t.role.name),avg(t.id)" +
        " from org.beangle.data.orm.model.TestUser t" +
        " where (t.member.middleName is not null or t.friends is not null or t.properties is not null)" +
        " and t.birthday is null and t.updatedAt = :v1 and t.role.name like :v2 and t.id > :v3" +
        " group by t.id,t.role.name order by t.role.name")
    }

    it("user define function") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e =>
        e.member.middleName is("len(_)=?", 2) or
          e.id.is("bitand(_,?)>0", 123)
      }
      val query = q.build()
      assert(query.statement == "select t from org.beangle.data.orm.model.TestUser t" +
        " where len(t.member.middleName)=:v1 or bitand(t.id,:v2)>0")
    }
    it("GenStatement") {
      val builder = OqlBuilder.from(classOf[NationBean], "test")
      builder.groupBy("test.name").having("count(*)>1").select("test.name")

      val query1 = builder.build()
      assert(query1.statement == "select test.name from " + classOf[NationBean].getName + " test group by test.name having count(*)>1")

      val query2 = builder.orderBy("test.name").build()
      assert(query2.statement == "select test.name from " + classOf[NationBean].getName + " test group by test.name having count(*)>1 order by test.name")
    }
    it("complex nested conditions with and/or/between") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e =>
        (e.member.middleName.like("x") or e.friends.isNull)
          .and((e.role.name.equal("r1") or e.role.name.equal("r2")).and(e.id.between(1, 10)))
          .and(e.birthday.isNotNull)
      }
      val query = q.build()
      assert(query.params.size == 5)
      assert(query.statement == "select t from " + classOf[TestUser].getName + " t" +
        " where (t.member.middleName like :v1 or t.friends is null)" +
        " and (t.role.name = :v2 or t.role.name = :v3) and t.id between :v4 and :v5" +
        " and t.birthday is not null")
    }

    it("aggregate and distinct in select/groupBy/orderBy") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.on { e =>
        q.select(sum(e.id), avg(e.id), min(e.id), max(e.id), count(e.friends), count(distinct(e.role.name)))
          .groupBy(e.member.middleName, e.role.name)
          .orderBy(e.role.name, e.id)
      }
      q.having("count(*)>1")
      val query = q.build()
      assert(query.statement == "select sum(t.id),avg(t.id),min(t.id),max(t.id),count(t.friends),count(distinct t.role.name)" +
        " from " + classOf[TestUser].getName + " t" +
        " group by t.member.middleName,t.role.name having count(*)>1" +
        " order by t.role.name,t.id")
    }

    it("database function via applyDynamic") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e =>
        e.lower(e.member.middleName).equal("x")
          .and(e.coalesce(e.id, 0L).gt(1))
      }
      val query = q.build()
      assert(query.params.size == 2)
      assert(query.statement == "select t from " + classOf[TestUser].getName + " t" +
        " where lower(t.member.middleName) = :v1 and coalesce(t.id, 0) > :v2")
    }

    it("stacked where calls") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e => e.member.middleName.isNotNull }
      q.where { e => e.id.gt(2) and e.birthday.isNull }
      val query = q.build()
      assert(query.statement == "select t from " + classOf[TestUser].getName + " t" +
        " where (t.member.middleName is not null) and (t.id > :v1 and t.birthday is null)")
    }

    it("custom function with multiple args") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e =>
        e.id.is("coalesce(_, ?) > ?", 1, 2) or
          e.member.middleName.is("substr(_, 1, 2)=?", "ab")
      }
      val query = q.build()
      assert(query.params.size == 3)
      assert(query.statement == "select t from " + classOf[TestUser].getName + " t" +
        " where coalesce(t.id, :v1) > :v2 or substr(t.member.middleName, 1, 2)=:v3")
    }
    it("collection queries via contains (elements) / containsKey (indices)") {
      val q = OqlBuilder.from(classOf[TestUser], "t")
      q.where { e =>
        e.tags.contains("x")
          .and(e.times.containsKey(1))
          .and(e.roles.contains(new TestRole()))
          .and(e.id.in(1, 2, 3))
      }
      val query = q.build()
      assert(query.params.size == 6)
      // contains 统一 elements（Map 为 values），Map 键查询用 containsKey -> indices
      assert(query.statement == "select t from " + classOf[TestUser].getName + " t" +
        " where :v1 in elements(t.tags) and :v2 in indices(t.times)" +
        " and :v3 in elements(t.roles) and t.id in (:v4, :v5, :v6)")
    }
  }

}