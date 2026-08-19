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

import org.beangle.data.dao.OqlBuilder
import org.beangle.data.hibernate.model.{ExtendRole, Member, Name, Role, User}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** scala.Dynamic OQL DSL 集成验证：用户示例形态（where 链式 and/or、on + select/groupBy/orderBy）在 H2 上真实执行 */
class DynamicOqlTest extends AnyFunSpec, Matchers {

  val ds = Tests.buildTestH2()
  val builder = new LocalSessionFactoryBean(ds)
  builder.ormLocation = "classpath*:beangle.xml"
  builder.properties.put("hibernate.hbm2ddl.auto", "create")
  builder.init()
  val sf = builder.getObject

  val entityDao = new HibernateEntityDao(sf)
  entityDao.init()
  SessionHelper.openSession(sf)

  it("where { e => e.x.equal(v).and(e.a.b.equal(v)).and(e.c.isNull or e.c.gt(v)) }") {
    val session = sf.getCurrentSession()
    val tx = session.beginTransaction()
    val u = new User(1)
    u.name = new Name
    u.name.first = "Bill"
    u.name.last = "Smith"
    u.member = new Member
    u.member.admin = false
    u.createdOn = java.sql.Date.valueOf("2021-06-01")
    entityDao.saveOrUpdate(u)
    session.flush()

    // 用户示例形态：e.project.equal(project).and(e.department.teaching.equal(false)).and(e.endOn.isNull or e.endOn.gt(...))
    val q = OqlBuilder.from(classOf[User], "u")
    q.where { e =>
      e.name.first.equal("Bill")
        .and(e.member.admin.equal(false))
        .and(e.createdOn.isNull or e.createdOn.gt(java.sql.Date.valueOf("2020-01-01")))
    }
    q.on { e =>
      q.select(e.name.first, e.name.last, "count(*)")
      q.groupBy(e.name.first, e.name.last)
      q.orderBy(e.name.first)
    }
    val list = entityDao.search(q)
    assert(list.size == 1, s"expected 1 row, got ${list.size}: ${q.build().statement}")
    tx.commit()
  }

  it("where with like/or/gt renders valid HQL") {
    val q = OqlBuilder.from(classOf[User], "u")
    q.where { e =>
      (e.name.first.like("Bi") or e.name.last.isNull)
        .and(e.role.isNull or e.createdOn.gt(java.sql.Date.valueOf("2019-01-01")))
    }
    val stmt = q.build().statement
    assert(stmt.contains("where (u.name.first like :v1 or u.name.last is null) and (u.role is null or u.createdOn > :v2)"), stmt)
    val list = entityDao.search(q)
    assert(list.size == 1)
  }
  it("database function (lower/coalesce) in where, executed on H2") {
    val session = sf.getCurrentSession()
    val tx = session.beginTransaction()
    val u = new User(2)
    u.name = new Name
    u.name.first = "Bill"
    u.name.last = "Smith"
    u.member = new Member
    u.createdOn = java.sql.Date.valueOf("2021-06-01")
    entityDao.saveOrUpdate(u)
    session.flush()

    val q = OqlBuilder.from(classOf[User], "u")
    q.where { e =>
      (e.lower(e.name.first).equal("bill") or e.name.last.isNull)
        .and(e.coalesce(e.id, 0L).gt(1))
    }
    val list = entityDao.search(q)
    assert(list.size == 1, s"expected 1 row: ${q.build().statement}")
    tx.commit()
  }

  it("aggregate select/groupBy/having executed on H2") {
    val session = sf.getCurrentSession()
    val tx = session.beginTransaction()
    val u1 = new User(3)
    u1.name = new Name
    u1.name.first = "Bill"
    u1.name.last = "Gates"
    u1.member = new Member
    entityDao.saveOrUpdate(u1)
    val u2 = new User(4)
    u2.name = new Name
    u2.name.first = "Alex"
    u2.name.last = "Chen"
    u2.member = new Member
    entityDao.saveOrUpdate(u2)
    session.flush()

    val q = OqlBuilder.from(classOf[User], "u")
    q.on { e =>
      q.select(e.name.first, "count(*)")
      q.groupBy(e.name.first)
    }
    q.having("count(*)>0")
    val bean = q.build()
    assert(bean.statement.contains("group by u.name.first having count(*)>0"), bean.statement)
    val tq = session.createQuery(bean.statement, classOf[Array[Object]])
    bean.params foreach { case (k, v) => tq.setParameter(k, v) }
    val rows = tq.list()
    assert(rows.size == 2, s"expected 2 groups: ${bean.statement}")
    tx.commit()
  }

  it("deep entity/component path in where, executed on H2") {
    val session = sf.getCurrentSession()
    val tx = session.beginTransaction()
    val grantor = new ExtendRole(1)
    grantor.name = "Grantor"
    entityDao.saveOrUpdate(grantor)
    val owner = new User(5)
    owner.name = new Name
    owner.name.first = "Bill"
    owner.name.last = "Smith"
    owner.member = new Member
    owner.member.granter = Some(grantor)
    entityDao.saveOrUpdate(owner)
    val r = new ExtendRole(2)
    r.name = "Owned"
    r.creator = Some(owner)
    entityDao.saveOrUpdate(r)
    session.flush()

    // e.creator.name.first（实体->组件） and e.creator.member.granter.name（实体->组件->实体->标量）
    val q = OqlBuilder.from(classOf[Role], "r")
    q.where { e =>
      e.creator.name.first.equal("Bill")
        .and(e.creator.member.granter.name.like("Grant%"))
    }
    val list = entityDao.search(q)
    assert(list.size == 1, s"expected 1 role: ${q.build().statement}")
    tx.commit()
  }
  it("collection queries: elements/indices/member of, executed on H2") {
    val session = sf.getCurrentSession()
    val tx = session.beginTransaction()
    val role = new ExtendRole(3)
    role.name = "Admin"
    entityDao.saveOrUpdate(role)
    val u = new User(6)
    u.name = new Name
    u.name.first = "Bill"
    u.name.last = "Smith"
    u.member = new Member
    u.properties = new scala.collection.mutable.HashMap[String, String]
    u.properties.put("address", "some street")
    val wt = new org.beangle.commons.lang.time.WeekTime()
    wt.startOn = java.time.LocalDate.now()
    wt.beginAt = org.beangle.commons.lang.time.HourMinute.Zero
    wt.endAt = org.beangle.commons.lang.time.HourMinute.Zero
    wt.weekstate = org.beangle.commons.lang.time.WeekState.apply("0110")
    u.times.put(1, wt)
    u.roleSet.add(role)
    entityDao.saveOrUpdate(u)
    session.flush()

    val q = OqlBuilder.from(classOf[User], "u")
    q.where { e =>
      e.properties.contains("some street")
        .and(e.times.containsKey(1))
        .and(e.roleSet.contains(role))
    }
    val stmt = q.build().statement
    // contains 统一 elements（Map 为 values），Map 键查询用 containsKey -> indices
    assert(stmt.contains(":v1 in elements(u.properties)"), stmt)
    assert(stmt.contains(":v2 in indices(u.times)"), stmt)
    assert(stmt.contains(":v3 in elements(u.roleSet)"), stmt)
    val list = entityDao.search(q)
    assert(list.size == 1, s"expected 1 user: $stmt")
    tx.commit()
  }
}