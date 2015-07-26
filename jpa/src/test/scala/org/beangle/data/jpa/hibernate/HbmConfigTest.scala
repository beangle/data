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

import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.jpa.hibernate.cfg.{ ConfigurationBuilder, OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.hibernate.dialect.Oracle10gDialect
import org.beangle.commons.io.IOs
import org.hibernate.cfg.AvailableSettings
import javax.sql.DataSource
import org.hibernate.dialect.H2Dialect
import java.{ util => ju }

@RunWith(classOf[JUnitRunner])
class HbmConfigTest extends FunSpec with Matchers {

  val properties = new ju.Properties
  properties.put(AvailableSettings.DIALECT, classOf[H2Dialect].getName)
  properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
  properties.put("hibernate.hbm2ddl.auto", "create")
  properties.put("hibernate.show_sql", "true")
  properties.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, classOf[SimpleCurrentSessionContext].getName())

  val configuration = new OverrideConfiguration
  configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))
  val builder = new ConfigurationBuilder(configuration, properties)
  builder.ormLocations = List(ClassLoaders.getResource("META-INF/beangle/orm.xml"))
  builder.build()
  val dbprops = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))
  val ds: DataSource = new PoolingDataSourceFactory(dbprops("h2.driverClassName"),
    dbprops("h2.url"), dbprops("h2.username"), dbprops("h2.password"), new java.util.Properties()).getObject

  new SessionFactoryBuilder(ds, configuration).build()

}