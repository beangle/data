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
package org.beangle.data.jpa.hibernate.cfg

import java.net.URL
import java.{ util => ju }

import org.beangle.commons.io.{ IOs, ResourcePatternResolver }
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.commons.logging.Logging
import org.beangle.data.model.bind.{ PersistModule, Binder }
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.hibernate.cfg.{ Configuration, Mappings, NamingStrategy }
import org.hibernate.cfg.AvailableSettings.DIALECT

import javax.persistence.Entity

object ConfigurationBuilder {

  def build(cfg: Configuration): Configuration = {
    val resolver = new ResourcePatternResolver
    val cfgBuilder = new ConfigurationBuilder(cfg)
    cfgBuilder.configLocations = resolver.getResources("classpath*:META-INF/hibernate.cfg.xml")
    cfgBuilder.ormLocations = resolver.getResources("classpath*:META-INF/beangle/orm.xml")
    val namingPolicy = new RailsNamingPolicy()
    for (resource <- cfgBuilder.ormLocations)
      namingPolicy.addConfig(resource)
    cfgBuilder.namingStrategy = new RailsNamingStrategy(namingPolicy)
    cfgBuilder.build()
    cfg.buildMappings()
    cfg
  }
}

class ConfigurationBuilder(val configuration: Configuration, val properties: ju.Properties = new ju.Properties) extends Logging {

  /**
   * Set the locations of multiple Hibernate XML config files, for example as
   * classpath resources "classpath:hibernate.cfg.xml,classpath:extension.cfg.xml".
   * <p>
   * Note: Can be omitted when all necessary properties and mapping resources are specified locally
   * via this bean.
   */
  var configLocations: Seq[URL] = List.empty

  /**
   * Set the locations of multiple persister.properties
   */
  var ormLocations: Seq[URL] = List.empty

  /**
   * Set a Hibernate NamingStrategy for the SessionFactory, determining the
   * physical column and table names given the info in the mapping document.
   */
  var namingStrategy: NamingStrategy = _

  /**
   * Import System properties and disable jdbc metadata lookup
   */
  private def processProperties() {
    // 1. Import system properties
    val sysProps = System.getProperties
    val keys = sysProps.propertyNames
    while (keys.hasMoreElements) {
      val key = keys.nextElement.asInstanceOf[String]
      if (key.startsWith("hibernate.")) {
        val value = sysProps.getProperty(key)
        val overrided = properties.containsKey(key)
        properties.put(key, value)
        if (overrided) logger.info(s"Override hibernate property $key=$value")
      }
    }
    import org.hibernate.cfg.AvailableSettings._
    // 2. disable metadata lookup
    // configuration.getProperties.put("hibernate.classLoader.application", beanClassLoader)
    // Disable JdbcServicesImpl magic behaviour except declare explicitly,
    // for it will slow startup performance. And it just consult medata's ddl semantic, which is seldom used.
    val useJdbcMetaName = "hibernate.temp.use_jdbc_metadata_defaults"
    if (properties.containsKey(DIALECT) && !properties.containsKey(useJdbcMetaName))
      properties.put(useJdbcMetaName, "false")

    configuration.addProperties(this.properties)
  }

  def build(): Configuration = {
    processProperties()

    if (null != this.namingStrategy) configuration.setNamingStrategy(this.namingStrategy)
    try {
      if (null != configLocations) {
        for (resource <- configLocations)
          configuration.configure(resource)
      }
      if (null != ormLocations) {
        val mappings = configuration.createMappings();
        for (resource <- ormLocations) {
          val is = resource.openStream
          (scala.xml.XML.load(is) \ "module") foreach { ele =>
            val moduleClass = (ele \ "@class").text
            val persistModule = Reflections.newInstance(ClassLoaders.loadClass(moduleClass)).asInstanceOf[PersistModule]
            addPersistInfo(persistModule.getConfig, mappings)
            //            val enumer = props.propertyNames.asInstanceOf[ju.Enumeration[String]]
            //            while (enumer.hasMoreElements) {
            //              val propertyName = enumer.nextElement
            //              configuration.setProperty(propertyName, props.getProperty(propertyName))
            //            }
          }
          IOs.close(is)
        }
      }
    } finally {
    }
    configuration
  }

  /**
   * Add annotation class from persist configuration
   */
  private def addPersistInfo(epconfig: Binder, mappings: Mappings) {
    for (definition <- epconfig.entities) {
      val clazz = definition.clazz
      if (clazz.isAnnotationPresent(classOf[javax.persistence.Entity])) {
        configuration.addAnnotatedClass(definition.clazz)
        logger.debug(s"Add annotation ${definition.clazz}")
      } else {
        new HbmConfigBinder(mappings).bindClass(definition)
      }

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


