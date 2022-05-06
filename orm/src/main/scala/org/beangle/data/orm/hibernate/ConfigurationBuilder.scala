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

import java.net.URL
import java.{util => ju}

import javax.sql.DataSource
import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.logging.Logging
import org.beangle.data.orm.hibernate.cfg.MappingService
import org.beangle.data.jdbc.engine.Engines
import org.beangle.data.jdbc.meta.Database
import org.beangle.data.orm.Mappings
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.{AvailableSettings, Configuration}

object ConfigurationBuilder {
  def default: Configuration = {
    val resolver = new ResourcePatternResolver
    val sfb = new ConfigurationBuilder(null)
    sfb.ormLocations = resolver.getResources("classpath*:META-INF/beangle/orm.xml")
    sfb.build()
  }
}

class ConfigurationBuilder(val dataSource: DataSource) extends Logging {

  var ormLocations: Seq[URL] = _

  var properties = new ju.Properties

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
    val useJdbcMetaName = "hibernate.temp.use_jdbc_metadata_defaults"
    if (properties.containsKey(AvailableSettings.DIALECT) && !properties.containsKey(useJdbcMetaName)) {
      properties.put(useJdbcMetaName, "false")
    } else {
      properties.put(useJdbcMetaName, "true")
    }
    if (dataSource != null) properties.put(AvailableSettings.DATASOURCE, dataSource)
    properties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD")
    properties.put("hibernate.ejb.metamodel.population", "disabled")
  }

  def build(): Configuration = {
    importSysProperties()
    customProperties()

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
    val engine =  Engines.forDataSource(dataSource)
    val mappings = new Mappings(new Database(engine), ormLocations.toList)
    mappings.autobind()
    mappings
  }

}
