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
package org.beangle.data.hibernate

import java.time.YearMonth

import org.beangle.commons.lang.time.WeekState
import org.beangle.data.dao.OqlBuilder
import org.beangle.data.hibernate.model._
import org.hibernate.SessionFactory

import scala.collection.mutable.ListBuffer

object UserCrudTest {

  def testCrud(sf: SessionFactory): Unit = {
    val entityDao = new HibernateEntityDao(sf)
    val session = sf.getCurrentSession
    val transaction = session.beginTransaction()
    val roles = entityDao.getAll(classOf[User])
    val user = new User(1)
    user.name = new Name
    user.name.first = "Bill"
    user.name.last = "Smith"
    user.createdOn = new java.sql.Date(System.currentTimeMillis())
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
    entityDao.saveOrUpdate(role1, role2, role3, role4, role41, user)

    val query = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", role1)
    val list = entityDao.search(query)
    assert(list.size == 1)

    val query1 = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", Some(role1))
    val list1 = entityDao.search(query1)
    assert(list1.size == 1)

    val query2 = OqlBuilder.from(classOf[Role], "r").where("r.parent is null")
    val list2 = entityDao.search(query2)
    assert(list2.size == 3)

    val query3 = OqlBuilder.from(classOf[User], "u").where("u.age = :age", 20)
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
    transaction.commit()

    session.close()
  }
}
