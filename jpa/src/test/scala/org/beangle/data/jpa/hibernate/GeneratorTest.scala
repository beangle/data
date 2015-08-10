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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.jpa.hibernate.cfg.{ OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.beangle.data.jpa.model.{ IntIdResource, LongDateIdResource, LongIdResource }
import org.hibernate.{ SessionFactory, SessionFactoryObserver }
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.dialect.PostgreSQL9Dialect
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GeneratorTest extends FunSpec with Matchers {

  val configuration = new OverrideConfiguration
  configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))
  configuration.addInputStream(ClassLoaders.getResourceAsStream("org/beangle/data/jpa/model/id.hbm.xml"))
  val configProperties = configuration.getProperties()
  configProperties.put(AvailableSettings.DIALECT, classOf[PostgreSQL9Dialect].getName)
  configProperties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
  configProperties.put("hibernate.hbm2ddl.auto", "create")
  configProperties.put("hibernate.show_sql", "false")

  val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))
  val ds: DataSource = new PoolingDataSourceFactory(properties("pg.driverClassName"),
    properties("pg.url"), properties("pg.username"), properties("pg.password"), new java.util.Properties()).getObject

  configProperties.put(AvailableSettings.DATASOURCE, ds)

  // do session factory build.
  val serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties).build()

  configuration.setSessionFactoryObserver(new SessionFactoryObserver {
    override def sessionFactoryCreated(factory: SessionFactory) {}
    override def sessionFactoryClosed(factory: SessionFactory) {
      StandardServiceRegistryBuilder.destroy(serviceRegistry)
    }
  })
  var pgReady = false
  try {
    ds.getConnection
  } catch {
    case e: Throwable => pgReady = false
  }
  if (pgReady) {
    val sf = configuration.buildSessionFactory(serviceRegistry)

    describe("Generator") {
      it("generate id auto increment") {
        val s = sf.openSession()
        val lresource = new LongIdResource();
        s.saveOrUpdate(lresource)

        val iresource = new IntIdResource();
        iresource.year = 2014
        s.saveOrUpdate(iresource)

        s.flush()
        s.close()
      }

      it("generate id by date") {
        val s = sf.openSession()
        val lresource = new LongDateIdResource();
        lresource.year = 2013
        s.saveOrUpdate(lresource)
        assert(lresource.id != null)
        assert(lresource.id.toString.startsWith("2013"))
        s.flush()
        s.close()
      }
    }
  }
}
