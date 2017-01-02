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

import java.{ util => ju }
import org.hibernate.cfg.AvailableSettings
import org.beangle.data.hibernate.cfg.OverrideConfiguration
import org.hibernate.dialect.H2Dialect
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.io.IOs
import org.beangle.data.jdbc.ds.DataSourceUtils
import javax.sql.DataSource
import org.beangle.data.hibernate.cfg.RailsNamingStrategy
import org.beangle.data.hibernate.naming.RailsNamingPolicy

object Tests {

  def buildProperties(): ju.Properties = {
    val properties = new ju.Properties
    properties.put(AvailableSettings.DIALECT, classOf[H2Dialect].getName)
    properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory")
    properties.put("hibernate.hbm2ddl.auto", "create")
    properties.put("hibernate.show_sql", "false")
    properties.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, classOf[SimpleCurrentSessionContext].getName())
    properties
  }

  def buildConfig(): OverrideConfiguration = {
    val configuration = new OverrideConfiguration
    configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))
    configuration
  }

  def buildDs(): DataSource = {
    val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))
    DataSourceUtils.build("h2", properties("h2.username"), properties("h2.password"), Map("url" -> properties("h2.url")))
  }
}
