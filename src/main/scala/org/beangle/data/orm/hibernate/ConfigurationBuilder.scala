/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.orm.hibernate

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.logging.Logging
import org.beangle.data.orm.{Mappings, Proxy}
import org.beangle.data.orm.hibernate.cfg.MappingService
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.Database
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.*
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode

import java.net.URL
import java.util as ju
import java.util.Properties
import javax.sql.DataSource

object ConfigurationBuilder {
  def default: Configuration = {
    val resolver = new ResourcePatternResolver
    val sfb = new ConfigurationBuilder(null)
    sfb.ormLocations = resolver.getResources("classpath*:META-INF/beangle/orm.xml")
    sfb.build()
  }
}

class ConfigurationBuilder(val dataSource: DataSource, properties: ju.Properties = new Properties()) extends Logging {

  var ormLocations: Seq[URL] = _

  /**
   * Import System properties
   */
  protected def importSysProperties(): Unit = {
    val sysProps = System.getProperties
    val keys = sysProps.propertyNames
    while (keys.hasMoreElements) {
      val key = keys.nextElement.asInstanceOf[String]
      if (key.startsWith("hibernate.")) {
        val value = sysProps.getProperty(key)
        val overridden = properties.containsKey(key)
        properties.put(key, value)
        if (overridden) logger.info(s"Override hibernate property $key=$value")
      }
    }
    if (!sysProps.contains("org.jboss.logging.provider")) {
      System.setProperty("org.jboss.logging.provider", "slf4j")
    }
  }

  protected def addDefaultProperties(): Unit = {
    addDefault(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "org.beangle.data.orm.hibernate.SpringSessionContext")

    //JdbcSettings
    if (dataSource != null) {
      properties.put(JdbcSettings.JAKARTA_JTA_DATASOURCE, dataSource)
      properties.put(JdbcSettings.DATASOURCE, dataSource)
    }
    addDefault(JdbcSettings.CONNECTION_HANDLING, PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION.name)
    //@see https://in.relation.to/2025/01/24/jdbc-fetch-size/
    addDefault(JdbcSettings.STATEMENT_FETCH_SIZE, "1000")
    addDefault(JdbcSettings.USE_GET_GENERATED_KEYS, "true")
    addDefault(JdbcSettings.SHOW_SQL, "false")
    addDefault(JdbcSettings.FORMAT_SQL, "false")

    //MappingSettings
    addDefault(MappingSettings.XML_MAPPING_ENABLED, "false")
    //addDefault(MappingSettings.JAVA_TIME_USE_DIRECT_JDBC, "true")

    //BatchSettings
    addDefault(BatchSettings.STATEMENT_BATCH_SIZE, "20")

    //FetchSettings
    addDefault(FetchSettings.MAX_FETCH_DEPTH, "1")
    addDefault(FetchSettings.DEFAULT_BATCH_FETCH_SIZE, "100")

    //CacheSettings
    addDefault(CacheSettings.USE_SECOND_LEVEL_CACHE, "true")
    addDefault(CacheSettings.USE_QUERY_CACHE, "true")
    addDefault(CacheSettings.CACHE_REGION_FACTORY, "jcache")
    addDefault(CacheSettings.AUTO_EVICT_COLLECTION_CACHE, "true")

    //PersistenceSettings
    addDefault(PersistenceSettings.SCANNER_DISCOVERY, "none")

    if (!properties.contains("hibernate.javax.cache.provider")) {
      addDefault("hibernate.javax.cache.missing_cache_strategy", "create")
      //config caffeine within application.conf(typesafe config file)
      val caffeine = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider"
      if ClassLoaders.get(caffeine).nonEmpty then addDefault("hibernate.javax.cache.provider", caffeine)
    }
  }

  def enableDevMode(): Unit = {
    addDefault(JdbcSettings.SHOW_SQL, "true")
    addDefault(JdbcSettings.LOG_SLOW_QUERY, "100") //100ms
  }

  private def addDefault(name: String, value: Any): Unit = {
    if !properties.containsKey(name) then properties.put(name, value)
  }

  def build(): Configuration = {
    addDefaultProperties()
    importSysProperties()
    val standardRegistryBuilder = new StandardServiceRegistryBuilder()
    val mappings = getMappings
    standardRegistryBuilder.addService(classOf[MappingService], new MappingService(mappings))
    standardRegistryBuilder.applySettings(this.properties)
    val standardRegistry = standardRegistryBuilder.build()

    val metadataSources = new MetadataSources(standardRegistry)
    val configuration = new Configuration(metadataSources)

    configuration.addProperties(this.properties)
    //clean proxy meta class
    Proxy.cleanup()
    configuration
  }

  private def getMappings: Mappings = {
    val engine = Engines.forDataSource(dataSource)
    val mappings = new Mappings(new Database(engine), ormLocations.toList)
    mappings.autobind()
    mappings
  }

}
