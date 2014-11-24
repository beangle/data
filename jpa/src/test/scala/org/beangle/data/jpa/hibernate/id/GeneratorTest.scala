package org.beangle.data.jpa.hibernate.id

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.annotation.description
import org.beangle.data.jpa.hibernate.{ OverrideConfiguration, RailsNamingStrategy }
import org.beangle.data.jpa.hibernate.udt.{ PoolingDataSourceFactory, UdtTest }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.beangle.data.jpa.model.{ IntIdResource, LongDateIdResource, LongIdResource }
import org.hibernate.{ SessionFactory, SessionFactoryObserver }
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.dialect.PostgreSQL9Dialect
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GeneratorTest extends FunSpec with Matchers {

  val configuration = new OverrideConfiguration()
  configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))
  val configProperties = configuration.getProperties()
  configProperties.put(AvailableSettings.DIALECT, classOf[PostgreSQL9Dialect].getName)
  configProperties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
  configProperties.put("hibernate.hbm2ddl.auto", "create")
  configProperties.put("hibernate.show_sql", "true")

  for (resource <- ClassLoaders.getResources("META-INF/hibernate.cfg.xml", classOf[UdtTest]))
    configuration.configure(resource)
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
      lresource.year = 2014
      s.saveOrUpdate(lresource)

      s.flush()
      s.close()
    }
  }
}
