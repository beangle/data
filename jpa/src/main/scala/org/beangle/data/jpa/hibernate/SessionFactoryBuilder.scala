/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jpa.hibernate

import java.io.IOException
import java.net.URL
import java.{ util => ju }
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.bind.AbstractPersistModule
import org.beangle.data.jpa.bind.EntityPersistConfig
import org.hibernate.SessionFactory
import org.hibernate.SessionFactoryObserver
import org.hibernate.cfg.AvailableSettings.DATASOURCE
import org.hibernate.cfg.AvailableSettings.DIALECT
import org.hibernate.cfg.Configuration
import org.hibernate.cfg.NamingStrategy
import org.hibernate.service.ServiceRegistryBuilder
import javax.sql.DataSource
import org.hibernate.boot.registry.StandardServiceRegistryBuilder

abstract class SessionFactoryBuilder extends Logging {
  def build(): SessionFactory;
}

class HbmSessionFactoryBuilder(val dataSource: DataSource, val properties: ju.Properties = new ju.Properties) extends SessionFactoryBuilder {
  /** static and global hbm mapping without namingstrategy */
  var staticHbm: URL = _

  def build(): SessionFactory = {
    val configuration = new Configuration
    configuration.addCacheableFile(staticHbm.getFile)
    import org.hibernate.cfg.AvailableSettings._
    if (dataSource != null) configuration.getProperties.put(DATASOURCE, dataSource)
    configuration.addProperties(this.properties)
    // do session factory build.
    val watch = new Stopwatch(true)
    val serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties).build()

    configuration.setSessionFactoryObserver(new SessionFactoryObserver {
      override def sessionFactoryCreated(factory: SessionFactory) {}
      override def sessionFactoryClosed(factory: SessionFactory) {
        StandardServiceRegistryBuilder.destroy(serviceRegistry)
      }
    })
    val sessionFactory = configuration.buildSessionFactory(serviceRegistry)
    logger.info("Building Hibernate SessionFactory in {}", watch)
    sessionFactory
  }

}

/**
 * @author chaostone
 * @version $Id: SessionFactoryBean.java Feb 27, 2012 10:52:27 PM chaostone $
 */
class DefaultSessionFactoryBuilder(val dataSource: DataSource, val configuration: Configuration, val properties: ju.Properties = new ju.Properties) extends SessionFactoryBuilder {

  /**
   * Set the locations of multiple Hibernate XML config files, for example as
   * classpath resources "classpath:hibernate.cfg.xml,classpath:extension.cfg.xml".
   * <p>
   * Note: Can be omitted when all necessary properties and mapping resources are specified locally
   * via this bean.
   *
   * @see org.hibernate.cfg.Configuration#configure(java.net.URL)
   */
  var configLocations: Seq[URL] = List.empty

  /**
   * Set the locations of multiple persister.properties
   */
  var persistLocations: Seq[URL] = List.empty

  /**
   * Set a Hibernate NamingStrategy for the SessionFactory, determining the
   * physical column and table names given the info in the mapping document.
   *
   * @see org.hibernate.cfg.Configuration#setNamingStrategy
   */
  var namingStrategy: NamingStrategy = _

  /**
   * Import System properties and disable jdbc metadata lookup
   */
  private def processProperties() {
    // 1. import system properties
    val sysProps = System.getProperties
    val keys = sysProps.propertyNames
    while (keys.hasMoreElements) {
      val key = keys.nextElement.asInstanceOf[String]
      if (key.startsWith("hibernate.")) {
        val value = sysProps.getProperty(key)
        val overrided = properties.containsKey(key)
        properties.put(key, value)
        if (overrided) logger.info("Override hibernate property {}={}", key, value)
      }
    }
    // 2. set datasource and disable metadata lookup
    // configuration.getProperties.put("hibernate.classLoader.application", beanClassLoader)
    import org.hibernate.cfg.AvailableSettings._
    if (dataSource != null) configuration.getProperties.put(DATASOURCE, dataSource)
    // Disable JdbcServicesImpl magic behaviour except declare explicitly,
    // for it will slow startup performance. And it just consult medata's ddl semantic, which is
    // seldom used.
    val useJdbcMetaName = "hibernate.temp.use_jdbc_metadata_defaults"
    if (properties.containsKey(DIALECT) && !properties.containsKey(useJdbcMetaName))
      properties.put(useJdbcMetaName, "false")

    configuration.addProperties(this.properties)
  }

  def build(): SessionFactory = {
    processProperties()

    if (null != this.namingStrategy) configuration.setNamingStrategy(this.namingStrategy)
    try {
      if (null != configLocations) {
        for (resource <- configLocations)
          configuration.configure(resource)
      }
      if (null != persistLocations) {
        for (resource <- persistLocations) {
          val is = resource.openStream
          val props = new ju.Properties
          if (null != is) props.load(is)

          val module = props.remove("module")
          if (null == module) {
            logger.warn("Cannot find module in {}", resource)
          } else {
            val persistModule = Reflections.newInstance(ClassLoaders.loadClass(module.toString)).asInstanceOf[AbstractPersistModule]
            addPersistInfo(persistModule.getConfig)
            val enumer = props.propertyNames.asInstanceOf[ju.Enumeration[String]]
            while (enumer.hasMoreElements) {
              val propertyName = enumer.nextElement
              configuration.setProperty(propertyName, props.getProperty(propertyName))
            }
          }
          IOs.close(is)
        }
      }
    } finally {
    }

    // do session factory build.
    val watch = new Stopwatch(true)
    val serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties).build()

    configuration.setSessionFactoryObserver(new SessionFactoryObserver {
      override def sessionFactoryCreated(factory: SessionFactory) {}
      override def sessionFactoryClosed(factory: SessionFactory) {
        StandardServiceRegistryBuilder.destroy(serviceRegistry)
      }
    })
    val sessionFactory = configuration.buildSessionFactory(serviceRegistry)
    logger.info("Building Hibernate SessionFactory in {}", watch)
    sessionFactory
  }

  /**
   * Add annotation class from persist configuration
   *
   * @param epconfig
   */
  private def addPersistInfo(epconfig: EntityPersistConfig) {
    for (definition <- epconfig.entities) {
      configuration.addAnnotatedClass(definition.clazz)
      logger.debug("Add annotation {}", definition.clazz)
      if (null != definition.cacheUsage) {
        val region = if (null == definition.cacheRegion) definition.entityName else definition.cacheRegion
        configuration.setCacheConcurrencyStrategy(definition.entityName, definition.cacheUsage, region, true)
      }
    }
    for (definition <- epconfig.collections if (null != definition.cacheUsage)) {
      val role = epconfig.getEntity(definition.clazz).entityName + "." + definition.property
      val region = if (null == definition.cacheRegion) role else definition.cacheRegion
      configuration.setCollectionCacheConcurrencyStrategy(role, definition.cacheUsage, region)
    }
  }
}
