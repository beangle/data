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

import org.beangle.commons.bean.Factory
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.time.{ HourMinute, WeekDay, WeekState }
import org.beangle.commons.logging.Logging
import org.beangle.data.hibernate.cfg.{ OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.hibernate.naming.RailsNamingPolicy
import org.beangle.data.hibernate.model.TimeBean
import org.hibernate.{ Session, SessionFactory, SessionFactoryObserver }
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.context.spi.AbstractCurrentSessionContext
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

import javax.sql.DataSource

@RunWith(classOf[JUnitRunner])
class UdtTest extends FunSpec with Matchers {

  val configuration = Tests.buildConfig()
  configuration.addInputStream(ClassLoaders.getResourceAsStream("org/beangle/data/hibernate/model/user.hbm.xml"))
  configuration.addInputStream(ClassLoaders.getResourceAsStream("org/beangle/data/hibernate/model/time.hbm.xml"))

  val configProperties = Tests.buildProperties()
  configProperties.put(AvailableSettings.DATASOURCE, Tests.buildDs())
  configuration.setProperties(configProperties)

  // do session factory build.
  val serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties).build()

  configuration.setSessionFactoryObserver(new SessionFactoryObserver {
    override def sessionFactoryCreated(factory: SessionFactory) {}
    override def sessionFactoryClosed(factory: SessionFactory) {
      StandardServiceRegistryBuilder.destroy(serviceRegistry)
    }
  })
  val sf = configuration.buildSessionFactory(serviceRegistry)

  describe("Beangle UDT") {
    it("Should support int? and scala collection") {
      UserCrudTest.testCrud(sf)
    }
    it("Support user defined time ") {
      val s = sf.openSession()
      val id = 1
      val timebean = new TimeBean()
      timebean.time = new HourMinute(1230.asInstanceOf[Short])
      timebean.weekday = WeekDay.Sun
      timebean.state = new WeekState(1)
      timebean.id = id
      s.save(timebean)
      assert(timebean.id > 0)
      s.flush()
      s.clear()
      val saved = s.get(classOf[TimeBean], id).asInstanceOf[TimeBean]
      assert(null != saved.time)
      assert(null != saved.weekday)
      assert(null != saved.state)
    }
  }
}

class SimpleCurrentSessionContext(factory: SessionFactoryImplementor) extends AbstractCurrentSessionContext(factory) {
  var session: Session = _
  def currentSession(): Session = {
    if (null == session || !session.isOpen()) {
      session = factory.openSession()
    }
    session
  }
}
