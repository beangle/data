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

import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.orm.hibernate.model.{ExtendRole, Role, User}
import org.beangle.data.model.meta.SingularProperty
import org.beangle.data.model.util.ConvertPopulator
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.springframework.core.io.UrlResource

class HibernateConfigTest extends AnyFunSpec with Matchers {

  val ormLocations = ClassLoaders.getResource("META-INF/beangle/orm.xml").toList
  val resouces = ormLocations map (url => new UrlResource(url.toURI))
  val ds = Tests.buildTestH2()
  val builder = new LocalSessionFactoryBean(ds)
  builder.ormLocations = resouces.toArray
  builder.properties.put("hibernate.show_sql", "true")
  builder.properties.put("hibernate.hbm2ddl.auto", "create")
  builder.init()

  val sf = builder.result

  val entityDao = new HibernateEntityDao(sf)
  val domain = entityDao.domain
  SessionHelper.openSession(sf)

  val roleMetaOption = domain.getEntity(classOf[Role])
  val userMetaOption = domain.getEntity(classOf[User])

  it("Should support option and collection") {
      UserCrudTest.testCrud(sf)
  }

  it("Role's parent is entityType") {
    assert(roleMetaOption.isDefined)
    val parentMeta = roleMetaOption.get.getProperty("parent")
    assert(parentMeta.isDefined)
    assert(parentMeta.get.isInstanceOf[SingularProperty])
    assert(parentMeta.get.asInstanceOf[SingularProperty].propertyType.clazz == classOf[ExtendRole])
  }

  it("populate to option entity") {
    val populator = new ConvertPopulator()
    val roleMeta = roleMetaOption.get
    val role = new Role();

    populator.populate(role, roleMeta, "parent.id", "1");
    assert(role.parent != null)
    assert(role.parent.isDefined)
    assert(role.parent.get.id == 1)

    role.parent = Some(new ExtendRole(1))
    val oldParent = role.parent.get
    populator.populate(role, roleMeta, "parent.id", "2");
    assert(role.parent != null)
    assert(role.parent.isDefined)
    assert(role.parent.get.id == 2)
    assert(oldParent.id == 1)

    populator.populate(role, roleMeta, "parent.id", "");
    assert(role.parent != null)
    assert(role.parent.isEmpty)

    val u = new User
    val userMeta = userMetaOption.get
    role.creator = Some(u)
    populator.populate(u, userMeta, "age", "2");
    assert(u.age.contains(2))
    populator.populate(u, userMeta, "age", "");
    assert(u.age.isEmpty)

    populator.populate(role, roleMeta, "creator.age", "2");
    assert(u.age.contains(2))
  }

  it("get java.sql.Date on Role.expiredOn") {
    val roleMeta = domain.getEntity(classOf[Role])
    assert(None != roleMeta)
    roleMeta.foreach { rm =>
      assert(classOf[java.sql.Timestamp] == rm.getProperty("updatedAt").get.clazz)
      assert(classOf[java.util.Date] == rm.getProperty("createdAt").get.clazz)
      assert(classOf[java.util.Calendar] == rm.getProperty("s").get.clazz)
      assert(classOf[java.sql.Date] == rm.getProperty("expiredOn").get.clazz)
    }
  }
}
