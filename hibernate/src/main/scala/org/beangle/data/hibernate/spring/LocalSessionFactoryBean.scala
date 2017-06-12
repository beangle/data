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
package org.beangle.data.hibernate.spring

import org.beangle.commons.bean.{ Factory, Initializing }
import org.beangle.commons.lang.annotation.description
import org.beangle.data.hibernate.ConfigurationBuilder
import org.hibernate.SessionFactory
import org.hibernate.cfg.AvailableSettings
import org.springframework.core.io.Resource
import java.{ util => ju }

import javax.sql.DataSource
import org.beangle.data.model.meta.Domain

@description("构建Hibernate的会话工厂")
class LocalSessionFactoryBean(val dataSource: DataSource) extends Factory[SessionFactory]
    with Initializing {

  var configLocations: Array[Resource] = Array.empty

  var ormLocations: Array[Resource] = Array.empty

  var properties = new ju.Properties

  var result: SessionFactory = _

  def init() {
    val cfgb = new ConfigurationBuilder(dataSource)
    //  provide the Beangle managed Session as context
    cfgb.configLocations = configLocations.map(l => l.getURL())
    cfgb.ormLocations = ormLocations.map(l => l.getURL())
    properties.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, classOf[BeangleSessionContext].getName)
    cfgb.properties = properties
    val config = cfgb.build()
    result = config.buildSessionFactory()
  }

}
