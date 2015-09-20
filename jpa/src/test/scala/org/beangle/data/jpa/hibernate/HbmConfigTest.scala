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

import java.{ util => ju }
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.annotation.description
import org.beangle.data.jpa.hibernate.cfg.{ ConfigurationBuilder, OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.hibernate.cfg.AvailableSettings
import org.hibernate.dialect.H2Dialect
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.jpa.model.Role

@RunWith(classOf[JUnitRunner])
class HbmConfigTest extends FunSpec with Matchers {

  val properties = new ju.Properties
  properties.put(AvailableSettings.DIALECT, classOf[H2Dialect].getName)
  properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
  properties.put("hibernate.hbm2ddl.auto", "create")
  properties.put("hibernate.show_sql", "true")
  properties.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, classOf[SimpleCurrentSessionContext].getName())

  val configuration = new OverrideConfiguration
  configuration.setInterceptor(new TestInterceptor)
  val builder = new ConfigurationBuilder(configuration, properties)
  val namingPolicy = new RailsNamingPolicy
  val ormLocations = List(ClassLoaders.getResource("META-INF/beangle/orm.xml"))
  ormLocations foreach (url => namingPolicy.addConfig(url))
  builder.namingStrategy = new RailsNamingStrategy(namingPolicy)
  builder.ormLocations = ormLocations
  builder.build()
  val dbprops = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))
  val ds: DataSource = DataSourceUtils.build("h2", dbprops("h2.username"), dbprops("h2.password"), Map("url" -> dbprops("h2.url")))
  val sf = new SessionFactoryBuilder(ds, configuration).build()
  val entityDao = new HibernateEntityDao(sf)

  it("Should support int? and scala collection") {
    UserCrudTest.testCrud(sf)
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