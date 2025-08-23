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

package org.beangle.data.orm.hibernate

import org.beangle.commons.json.Json
import org.beangle.commons.lang.time.{HourMinute, WeekState, WeekTime}
import org.beangle.data.dao.OqlBuilder
import org.beangle.data.orm.model.*
import org.beangle.data.orm.{AccessLog, AccessParam}
import org.hibernate.SessionFactory

import java.time.{LocalDate, YearMonth}
import scala.collection.mutable.ListBuffer

object UserCrud {

  def testCrud(sf: SessionFactory): Unit = {
    val entityDao = new HibernateEntityDao(sf)
    entityDao.init()
    val session = sf.getCurrentSession()
    val transaction = session.beginTransaction()
    val user = new User(1)
    user.name = new Name
    user.name.first = "Bill"
    user.name.last = "Smith"
    user.createdOn = new java.sql.Date(System.currentTimeMillis())
    val wt = new WeekTime()
    wt.startOn = LocalDate.now()
    wt.beginAt = HourMinute.Zero
    wt.endAt = HourMinute.Zero
    wt.weekstate = WeekState.apply("0110")
    user.times.put(1, wt)
    val role1 = new ExtendRole(1)
    val role2 = new ExtendRole(2)
    val role3 = new ExtendRole(3)

    val role4 = new ExtendRole(4)
    val role41 = new ExtendRole(41)
    role1.enName = "role1"
    role2.enName = "role2"
    role3.enName = "role3"

    role2.startOn = Some(YearMonth.parse("2019-02"))
    role2.properties.put(3, false)

    role4.enName = "role4"
    role4.children += role41
    role41.enName = "role41"
    role41.parent = Some(role4)

    user.roleSet.add(role1)
    user.roleSet.add(role2)
    user.roleList.asInstanceOf[ListBuffer[Role]] += role1
    user.roleList.asInstanceOf[ListBuffer[Role]] += role2
    user.roleList.asInstanceOf[ListBuffer[Role]] += role3

    user.age = Some(20)
    user.member = new Member
    user.properties = new collection.mutable.HashMap[String, String]
    user.properties.put("address", "some street")
    user.occupy = new WeekState(2)
    role2.parent = Some(role1)
    entityDao.saveOrUpdate(role1, role2, role3, role4, role41)
    entityDao.saveOrUpdate(user)
    session.flush()
    user.name.first = "1"
    entityDao.saveOrUpdate(user)
    session.flush()
    user.age = Some(21)
    entityDao.saveOrUpdate(user)
    session.flush()
    entityDao.refresh(user)
    //test add profiles
    assert(user.profiles.isEmpty)
    user.profiles.add(new Profile(user, "p1", "v1"))
    user.profiles.add(new Profile(user, "p2", "v2"))
    entityDao.saveOrUpdate(user)
    session.flush()
    entityDao.refresh(user)
    assert(user.profiles.size == 2)
    //test json
    user.friends = Json.parseArray("""[{"gender":"Male","name":"Json"},{"gender":"Female","name":"Alex"}]""")
    user.charactor = Json.parseObject("""{"favorite":"reading,skating","color":"red"}""")
    entityDao.saveOrUpdate(user)
    session.flush()
    entityDao.refresh(user)
    assert(user.friends.query("[1].name").contains("Alex"))
    assert(user.charactor.query("color").contains("red"))

    //test query
    val query = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", role1)
    val list = entityDao.search(query)
    assert(list.size == 1)

    val query1 = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", Some(role1))
    val list1 = entityDao.search(query1)
    assert(list1.size == 1)

    val query2 = OqlBuilder.from(classOf[Role], "r").where("r.parent is null")
    val list2 = entityDao.search(query2)
    assert(list2.size == 3)

    val query3 = OqlBuilder.from(classOf[User], "u").where("u.age = :age", 21)
    val list3 = entityDao.search(query3)
    assert(list3.size == 1)

    val query4 = OqlBuilder.from(classOf[Role], "r").where("exists(from " + classOf[Role].getName + " r2 where r2=r.parent)")
    val list4 = entityDao.search(query4)
    assert(list4.size == 2)

    val query5 = OqlBuilder.from(classOf[Role], "r")
    query5.where("r.parent.name like :roleName", "Role%")
    val list5 = entityDao.search(query5)
    assert(list5.size == 2)

    session.flush()
    session.clear()

    val query8 = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", role1)
    query8.where("key(r.properties)=1")
    val list8 = entityDao.search(query8)

    val saved = entityDao.get(classOf[User], user.id)
    assert(saved.properties.size == 1)
    assert(saved.roleSet.size == 2)
    assert(saved.roleList.size == 3)
    assert(null != saved.member.user)
    saved.roleSet.remove(saved.roleSet.iterator.next())
    entityDao.saveOrUpdate(saved);

    val savedRole = entityDao.get(classOf[Role], role2.id)
    assert(savedRole.parent != null)
    assert(savedRole.parent.isDefined)
    assert(savedRole.parent.get.id == role1.id)
    assert(savedRole.parent.get.asInstanceOf[ExtendRole].enName == "role1")

    val savedRole4 = entityDao.get(classOf[Role], role4.id)
    savedRole4.children -= role41
    entityDao.saveOrUpdate(savedRole4)
    session.flush()

    // test object collections
    val course = new Course()
    course.name = "course 1"
    //course.category = Some(CourseCategory.Practical)
    course.addLevel(1)
    course.addFeature("f1", "feature 1")
    course.addFeature("f2", "feature 2")
    entityDao.saveOrUpdate(course)
    session.flush()
    course.addLevel(2)
    course.addFeature("f1", "feature 1 rename")
    entityDao.saveOrUpdate(course)
    val courseId = course.id
    session.flush()
    session.clear()
    val c = session.find(classOf[Course], courseId)
    assert(c.features.size == 2)
    assert(c.hasFeature("f1", "feature 1 rename"))

    val accessLog = new AccessLog()
    accessLog.username = "admin"
    accessLog.action = "update"
    accessLog.resource = "user management"
    accessLog.ip = "localhost"
    accessLog.userAgent = "firefox"
    accessLog.updatedOn = LocalDate.now()
    accessLog.addParam(1L, "id", "1")
    accessLog.id = 1L
    entityDao.saveOrUpdate(accessLog)

    accessLog.resource = "user-management"
    entityDao.saveOrUpdate(accessLog)
    transaction.commit()
    session.clear()

    //test bitand on weekstate
    val cquery = session.createQuery(s"from ${classOf[Course].getName} c where coalesce(c.weekstate,:weekstate)>0 or c.weekstate=:weekstate", classOf[Course])
    cquery.setParameter("weekstate", WeekState.of(1, 2, 3))
    val clist = cquery.list()

    val allCoursesQuery = session.createQuery(s"from ${classOf[Course].getName}", classOf[Course])
    val allCourses = allCoursesQuery.list()
    assert(!allCourses.isEmpty)
    assert(allCourses.get(0).category.isEmpty)

    val logParams = entityDao.getAll(classOf[AccessParam])
    assert(logParams.head.log.username == "admin")
    //    assert(logs.head.params.size == 1)
    //    assert(logs.head.params.head.name == "id")
  }
}
