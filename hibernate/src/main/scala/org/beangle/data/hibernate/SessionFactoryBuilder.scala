/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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

import java.net.URL
import java.{ util => ju }

import org.beangle.commons.bean.{ Factory, Initializing }
import org.beangle.commons.io.{ IOs, ResourcePatternResolver }
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.commons.logging.Logging
import org.beangle.data.hibernate.cfg.BindMetadataSources
import org.hibernate.SessionFactory
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.{ AvailableSettings, Configuration }

import javax.sql.DataSource
import org.beangle.commons.orm.Mappings
import org.beangle.commons.orm.MappingModule
import org.beangle.commons.orm.NamingPolicy
import org.beangle.commons.jdbc.Engines
import org.beangle.commons.jdbc.Database
import org.beangle.data.hibernate.cfg.MappingService

object SessionFactoryBuilder {
  def defaultConfig(): Configuration = {
    val resolver = new ResourcePatternResolver
    val sfb = new SessionFactoryBuilder(null)
    sfb.configLocations = resolver.getResources("classpath*:META-INF/hibernate.cfg.xml")
    sfb.ormLocations = resolver.getResources("classpath*:META-INF/beangle/orm.xml")
    //    val namingPolicy = new RailsNamingPolicy()
    //    for (resource <- cfgBuilder.ormLocations)
    //      namingPolicy.addConfig(resource)
    //    cfgBuilder.namingStrategy = new RailsNamingStrategy(namingPolicy)
    sfb.buildConfig()
  }
}

class SessionFactoryBuilder(val dataSource: DataSource) extends Factory[SessionFactory]
    with Initializing with Logging {

  var configLocations: Seq[URL] = _

  var ormLocations: Seq[URL] = _

  var properties = new ju.Properties

  var namingPolicy: NamingPolicy = _

  private var configuration: Configuration = _

  /**
   * Import System properties and disable jdbc metadata lookup
   */
  protected def importSysProperties(): Unit = {
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
  }

  protected def customProperties(): Unit = {
    // 2. disable metadata lookup
    // configuration.getProperties.put("hibernate.classLoader.application", beanClassLoader)
    // Disable JdbcServicesImpl magic behaviour except declare explicitly,
    // for it will slow startup performance. And it just consult medata's ddl semantic, which is seldom used.
    val useJdbcMetaName = "hibernate.temp.use_jdbc_metadata_defaults"
    if (properties.containsKey(AvailableSettings.DIALECT) && !properties.containsKey(useJdbcMetaName))
      properties.put(useJdbcMetaName, "false")
    if (dataSource != null) properties.put(AvailableSettings.DATASOURCE, dataSource)
    properties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD");

    //    if (!classLoaders.isEmpty)
    //      config.getProperties.put(AvailableSettings.CLASSLOADERS, collection.JavaConverters.asJavaCollection(classLoaders))
  }

  def buildConfig(): Configuration = {
    val standardRegistryBuilder = new StandardServiceRegistryBuilder()
    val mappings = getMappings()
    standardRegistryBuilder.addService(classOf[MappingService], new MappingService(mappings))
    if (null != configLocations) {
      for (resource <- configLocations)
        standardRegistryBuilder.configure(resource)
    }
    val standardRegistry = standardRegistryBuilder.build()

    val metadataSources = new BindMetadataSources(mappings, standardRegistry)
    metadataSources.getMetadataBuilder.applyImplicitNamingStrategy(ImplicitNamingStrategyJpaCompliantImpl.INSTANCE)
    configuration = new Configuration(metadataSources)
    importSysProperties()
    customProperties()
    configuration.addProperties(this.properties)
    configuration
  }

  private def getMappings(): Mappings = {
    val connection = dataSource.getConnection
    val engine = Engines.forDatabase(connection.getMetaData.getDatabaseProductName)
    val mappings = new Mappings(new Database(engine), namingPolicy)
    try {
      if (null != ormLocations) {
        for (resource <- ormLocations) {
          val is = resource.openStream
          (scala.xml.XML.load(is) \ "mapping") foreach { ele =>
            Reflections.getInstance[MappingModule]((ele \ "@class").text).configure(mappings)
          }
          IOs.close(is)
        }
        mappings.autobind()
      }
    } finally {
      IOs.close(connection)
    }
    mappings
  }

  def result(): SessionFactory = {
    if (null == configuration) buildConfig()
    configuration.buildSessionFactory()
  }
}
