/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.jpa.hibernate

import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable.ListBuffer

import org.beangle.commons.lang.time.WeekState
import org.beangle.data.jpa.model.{ ExtendRole, Member, Name, Role, User }
import org.hibernate.SessionFactory

object UserCrudTest {

  def testCrud(sf: SessionFactory) {
    val entityDao = new HibernateEntityDao(sf)
    import scala.collection.JavaConversions._
    val user = new User(1)
    user.name = new Name
    user.name.first = "Bill"
    user.name.last = "Smith"
    user.createdOn = new java.sql.Date(System.currentTimeMillis())
    val role1 = new ExtendRole(1)
    val role2 = new ExtendRole(2)
    user.roleSet += role1
    user.roleSet += role2
    user.roleList.asInstanceOf[ListBuffer[Role]] += role1
    user.age = Some(20)
    user.member = new Member
    user.properties = new collection.mutable.HashMap[String, String]
    user.properties.put("address", "some street")
    user.occupy = new WeekState(2)
    entityDao.saveOrUpdate(role1, role2, user)
    sf.getCurrentSession.flush()
    sf.getCurrentSession.clear()

    val saved = entityDao.get(classOf[User], user.id).asInstanceOf[User]
    assert(saved.properties.size == 1)
    assert(saved.roleSet.size == 2)
    assert(saved.roleList.size == 1)
    assert(null!=saved.member.user)
    saved.roleSet -= saved.roleSet.head
    entityDao.saveOrUpdate(saved);
    sf.getCurrentSession.flush()
    sf.getCurrentSession.close()
  }
}