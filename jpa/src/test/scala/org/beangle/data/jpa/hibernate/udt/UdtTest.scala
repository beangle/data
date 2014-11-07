package org.beangle.data.jpa.hibernate.udt

import java.util.Properties
import scala.collection.mutable.ListBuffer
import org.apache.commons.dbcp.{ ConnectionFactory, DriverManagerConnectionFactory, PoolableConnectionFactory, PoolingDataSource }
import org.apache.commons.pool.impl.GenericObjectPool
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.jpa.hibernate.OverrideConfiguration
import org.beangle.data.jpa.model.{ Role, User }
import org.hibernate.{ SessionFactory, SessionFactoryObserver }
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.dialect.Oracle10gDialect
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UdtTest extends FunSpec with Matchers {

  describe("Beangle UDT") {
    it("Should support int? and scala collection") {
      val configuration = new OverrideConfiguration()
      val configProperties = configuration.getProperties()
      configProperties.put(AvailableSettings.DIALECT, classOf[Oracle10gDialect].getName)
      configProperties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
      configProperties.put("hibernate.hbm2ddl.auto", "create")
      configProperties.put("hibernate.show_sql", "true")

      for (resource <- ClassLoaders.getResources("META-INF/hibernate.cfg.xml", classOf[UdtTest]))
        configuration.configure(resource)
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

      val s = sf.openSession()
      val user = new User(1)
      val role = new Role(1)
      user.roles += role
      user.role2s.asInstanceOf[ListBuffer[Role]] += role
      user.age = Some(20)
      user.properties = new collection.mutable.HashMap[String, String]
      user.properties.put("address", "some street")
      s.save(role)
      s.save(user)
      s.flush()
      s.clear()
      val saved = s.get(classOf[User], user.id).asInstanceOf[User]
      assert(saved.properties.size == 1)
      s.close()
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
