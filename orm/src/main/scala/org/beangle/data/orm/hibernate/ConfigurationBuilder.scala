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
import org.beangle.data.jdbc.engine.Engines
import org.beangle.data.jdbc.meta.Database
import org.beangle.data.orm.Mappings
import org.beangle.data.orm.hibernate.cfg.MappingService
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings.*
import org.hibernate.cfg.Configuration
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

class ConfigurationBuilder(val dataSource: DataSource,properties :ju.Properties = new Properties()) extends Logging {

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
        val overrided = properties.containsKey(key)
        properties.put(key, value)
        if (overrided) logger.info(s"Override hibernate property $key=$value")
      }
    }
  }

  protected def addDefaultProperties(): Unit = {
    //properties.put("hibernate.type.preferred_instant_jdbc_type", "TIMESTAMP")
    if (dataSource != null) properties.put(DATASOURCE, dataSource)
    addDefault(CONNECTION_HANDLING, PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION.name)
    addDefault(CURRENT_SESSION_CONTEXT_CLASS, "org.beangle.data.orm.hibernate.SpringSessionContext")
    addDefault(SCANNER_DISCOVERY, "none")
    addDefault(XML_MAPPING_ENABLED, "false")

    addDefault(MAX_FETCH_DEPTH, "1")
    addDefault(STATEMENT_BATCH_SIZE, "20")
    addDefault(STATEMENT_FETCH_SIZE, "500")
    addDefault(DEFAULT_BATCH_FETCH_SIZE, "100")
    addDefault(USE_GET_GENERATED_KEYS, "true")

    addDefault(USE_SECOND_LEVEL_CACHE, "true")
    addDefault(USE_QUERY_CACHE, "true")
    addDefault(CACHE_REGION_FACTORY, "jcache")

    if (!properties.contains("hibernate.javax.cache.provider")) {
      addDefault("hibernate.javax.cache.missing_cache_strategy", "create")
      val caffeine = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider"
      if ClassLoaders.get(caffeine).nonEmpty then addDefault("hibernate.javax.cache.provider", caffeine)
    }

    addDefault(SHOW_SQL, "false")
    addDefault(FORMAT_SQL, "false")
  }

  def enableDevMode(): Unit = {
    addDefault(SHOW_SQL, "true")
    addDefault(LOG_SLOW_QUERY, "100") //100ms
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
    configuration
  }

  private def getMappings: Mappings = {
    val engine = Engines.forDataSource(dataSource)
    val mappings = new Mappings(new Database(engine), ormLocations.toList)
    mappings.autobind()
    mappings
  }

}
