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

import java.util.Properties
import scala.collection.mutable.ListBuffer
import org.apache.commons.dbcp.{ ConnectionFactory, DriverManagerConnectionFactory, PoolableConnectionFactory, PoolingDataSource }
import org.apache.commons.pool.impl.GenericObjectPool
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.time.{ HourMinute, WeekDay, WeekState }
import org.beangle.data.jpa.hibernate.cfg.{ OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.beangle.data.jpa.model.{ ExtendRole, Name, Role, TimeBean, User }
import org.hibernate.{ Session, SessionFactory, SessionFactoryObserver }
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.context.spi.AbstractCurrentSessionContext
import org.hibernate.dialect.Oracle10gDialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UdtTest extends FunSpec with Matchers {
  val configuration = new OverrideConfiguration
  configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))
  val configProperties = configuration.getProperties()
  configProperties.put(AvailableSettings.DIALECT, classOf[Oracle10gDialect].getName)
  configProperties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
  configProperties.put("hibernate.hbm2ddl.auto", "create")
  configProperties.put("hibernate.show_sql", "true")
  configuration.addInputStream(ClassLoaders.getResourceAsStream("org/beangle/data/jpa/model/model.hbm.xml"))
  configuration.addInputStream(ClassLoaders.getResourceAsStream("org/beangle/data/jpa/model/time.hbm.xml"))
  configProperties.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, classOf[SimpleCurrentSessionContext].getName())
  val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))
  val ds: DataSource = new PoolingDataSourceFactory(properties("h2.driverClassName"),
    properties("h2.url"), properties("h2.username"), properties("h2.password"), new java.util.Properties()).getObject
  configProperties.put(AvailableSettings.DATASOURCE, ds)

  // do session factory build.
  val serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties).build()

  configuration.setSessionFactoryObserver(new SessionFactoryObserver {
    override def sessionFactoryCreated(factory: SessionFactory) {}
    override def sessionFactoryClosed(factory: SessionFactory) {
      StandardServiceRegistryBuilder.destroy(serviceRegistry)
    }
  })
  val sf = configuration.buildSessionFactory(serviceRegistry)
  val entityDao = new HibernateEntityDao(sf)

  describe("Beangle UDT") {
    it("Should support int? and scala collection") {
      //      val s = sf.openSession().asInstanceOf[SessionImpl]
      val user = new User(1)
      user.name = new Name
      user.name.first = "Bill"
      user.name.last = "Smith"
      val role1 = new ExtendRole(1)
      val role2 = new ExtendRole(2)
      user.roleSet += role1
      user.roleSet += role2
      user.roleList.asInstanceOf[ListBuffer[Role]] += role1
      user.age = Some(20)
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
      saved.roleSet -= saved.roleSet.head
      entityDao.saveOrUpdate(saved);
      sf.getCurrentSession.flush()
      sf.getCurrentSession.close()
    }
    it("Support user defined time ") {
      val s = sf.openSession()
      val id = 1
      val timebean = new TimeBean()
      timebean.time = HourMinute(1230.asInstanceOf[Short])
      timebean.weekday = WeekDay.Sun
      timebean.state = new WeekState(1)
      timebean.id = id
      s.save(timebean)
      assert(null != timebean.id)
      s.flush()
      s.clear()
      val saved = s.get(classOf[TimeBean], id).asInstanceOf[TimeBean]
      assert(null != saved.time)
      assert(null != saved.weekday)
      assert(null != saved.state)
    }
  }
}

class PoolingDataSourceFactory(url: String, username: String, password: String, props: Properties) {

  val properties = if (null == props) new Properties() else new Properties(props)

  if (null != username) properties.put("user", username)
  if (null != password) properties.put("password", password)

  def this(newDriverClassName: String, url: String, username: String, password: String, props: Properties) = {
    this(url, username, password, props)
    registeDriver(newDriverClassName)
  }

  def registeDriver(newDriverClassName: String) = {
    //Validate.notEmpty(newDriverClassName, "Property 'driverClassName' must not be empty")
    val driverClassNameToUse: String = newDriverClassName.trim()
    try {
      Class.forName(driverClassNameToUse)
    } catch {
      case ex: ClassNotFoundException =>
        throw new IllegalStateException(
          "Could not load JDBC driver class [" + driverClassNameToUse + "]", ex)
    }
  }

  def getObject: DataSource = {
    val config = new GenericObjectPool.Config()
    config.maxActive = 16
    val connectionPool = new GenericObjectPool(null, config)
    val connectionFactory: ConnectionFactory = new DriverManagerConnectionFactory(url, properties)
    new PoolableConnectionFactory(connectionFactory, connectionPool, null, null, false, true)
    new PoolingDataSource(connectionPool)
  }

  def singleton = true

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
