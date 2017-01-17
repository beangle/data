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
package org.beangle.data.hibernate

import scala.collection.mutable.ListBuffer

import org.beangle.commons.lang.time.WeekState
import org.beangle.commons.dao.OqlBuilder
import org.beangle.data.hibernate.model.{ ExtendRole, Member, Name, Role, User }
import org.hibernate.SessionFactory

object UserCrudTest {

  def testCrud(sf: SessionFactory) {
    val entityDao = new HibernateEntityDao(sf)
    val roles = entityDao.getAll(classOf[User])
    val user = new User(1)
    user.name = new Name
    user.name.first = "Bill"
    user.name.last = "Smith"
    user.createdOn = new java.sql.Date(System.currentTimeMillis())
    val role1 = new ExtendRole(1)
    val role2 = new ExtendRole(2)
    val role3 = new ExtendRole(3)
    role1.enName = "role1"
    role2.enName = "role2"
    role3.enName = "role3"
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
    entityDao.saveOrUpdate(role1, role2, role3, user)

    val query = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", role1)
    val list = entityDao.search(query)
    assert(list.size == 1)

    val query1 = OqlBuilder.from(classOf[Role], "r").where("r.parent = :parent", Some(role1))
    val list1 = entityDao.search(query1)
    assert(list1.size == 1)

    val query2 = OqlBuilder.from(classOf[Role], "r").where("r.parent is null")
    val list2 = entityDao.search(query2)
    assert(list2.size == 2)

    val query3 = OqlBuilder.from(classOf[User], "u").where("u.age = :age", 20)
    val list3 = entityDao.search(query3)
    assert(list3.size == 1)

    val query4 = OqlBuilder.from(classOf[Role], "r").where("exists(from " + classOf[Role].getName + " r2 where r2=r.parent)")
    val list4 = entityDao.search(query4)
    assert(list4.size == 1)

    val query5 = OqlBuilder.from(classOf[Role], "r")
    query5.where("r.parent.name like :roleName", "Role%")
    val list5 = entityDao.search(query5)
    assert(list5.size == 1)

    sf.getCurrentSession.flush()
    sf.getCurrentSession.clear()

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

    sf.getCurrentSession.flush()
    sf.getCurrentSession.close()
  }
}
