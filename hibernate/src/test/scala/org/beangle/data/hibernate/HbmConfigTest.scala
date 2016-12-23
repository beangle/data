/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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

import java.sql.{ Date, Timestamp }
import java.{ util => ju }
import java.util.{ Calendar, Date }

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.hibernate.cfg.{ ConfigurationBuilder, OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.hibernate.model.{ ExtendRole, Role, User }
import org.beangle.data.hibernate.naming.RailsNamingPolicy
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.commons.model.meta.EntityType
import org.beangle.commons.model.util.ConvertPopulator

import org.hibernate.dialect.H2Dialect
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

import javax.sql.DataSource

@RunWith(classOf[JUnitRunner])
class HbmConfigTest extends FunSpec with Matchers {

  val configuration = Tests.buildConfig()
  configuration.setInterceptor(new TestInterceptor)
  val builder = new ConfigurationBuilder(configuration, Tests.buildProperties())
  val ormLocations = List(ClassLoaders.getResource("META-INF/beangle/orm.xml"))
  val namingPolicy = new RailsNamingPolicy
  ormLocations foreach (url => namingPolicy.addConfig(url))
  builder.namingStrategy = new RailsNamingStrategy(namingPolicy)
  builder.ormLocations = ormLocations
  builder.build()

  val ds = Tests.buildDs()
  val sf = new SessionFactoryBuilder(ds, configuration).build()
  val entityDao = new HibernateEntityDao(sf)

  val metadata = entityDao.metadata
  val roleMetaOption = metadata.getType(classOf[Role])
  val userMetaOption = metadata.getType(classOf[User])

  it("Should support int? and scala collection") {
    UserCrudTest.testCrud(sf)
  }

  it("Role's parent is entityType") {
    assert(None != roleMetaOption)
    val parentMeta = roleMetaOption.get.getPropertyType("parent")
    assert(None != parentMeta)
    assert(parentMeta.get.isEntityType)
    assert(parentMeta.get.asInstanceOf[EntityType].entityClass == classOf[ExtendRole])
  }

  it("populate to option entity") {
    val populator = new ConvertPopulator()
    val roleMeta = roleMetaOption.get
    val role = new Role();

    populator.populate(role, roleMeta, "parent.id", "1");
    assert(role.parent != null)
    assert(role.parent != None)
    assert(role.parent.get.id == 1)

    role.parent = Some(new ExtendRole(1))
    val oldParent = role.parent.get
    populator.populate(role, roleMeta, "parent.id", "2");
    assert(role.parent != null)
    assert(role.parent != None)
    assert(role.parent.get.id == 2)
    assert(oldParent.id == 1)

    populator.populate(role, roleMeta, "parent.id", "");
    assert(role.parent != null)
    assert(role.parent == None)

    val u = new User
    val userMeta = userMetaOption.get
    role.creator = Some(u)
    populator.populate(u, userMeta, "age", "2");
    assert(u.age == Some(2))
    populator.populate(u, userMeta, "age", "");
    assert(u.age == None)

    populator.populate(role, roleMeta, "creator.age", "2");
    assert(u.age == Some(2))
  }

  it("get java.sql.Date on Role.expiredOn") {
    val entityMeta = new EntityMetadataBuilder(List(sf)).build()
    val roleMeta = entityMeta.getType(classOf[Role])
    assert(None != roleMeta)
    roleMeta.foreach { rm =>
      assert(classOf[java.sql.Timestamp] == rm.getPropertyType("updatedAt").get.returnedClass)
      assert(classOf[java.util.Date] == rm.getPropertyType("createdAt").get.returnedClass)
      assert(classOf[java.util.Calendar] == rm.getPropertyType("s").get.returnedClass)
      assert(classOf[java.sql.Date] == rm.getPropertyType("expiredOn").get.returnedClass)
    }
  }
}
