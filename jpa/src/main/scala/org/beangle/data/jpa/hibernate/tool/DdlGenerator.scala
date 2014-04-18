/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.jpa.hibernate.tool

import java.io.IOException
import org.hibernate.HibernateException
import org.hibernate.cfg.Configuration
import org.hibernate.cfg.Environment
import org.hibernate.cfg.AvailableSettings
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.beangle.data.jpa.mapping.RailsNamingPolicy
import org.beangle.data.jpa.hibernate.RailsNamingStrategy
import org.beangle.data.jpa.hibernate.OverrideConfiguration
import org.beangle.commons.lang.ClassLoaders

/**
 * @author chaostone
 */
object DdlGenerator {

  def gen(dialect: String, fileName: String) {
    val configuration = new OverrideConfiguration()
    configuration.getProperties().put(AvailableSettings.DIALECT, dialect)
    val tableNamingPolicy = new RailsNamingPolicy
    for (resource <- ClassLoaders.getResources("META-INF/beangle/table.properties", classOf[DdlGenerator]))
      tableNamingPolicy.addConfig(resource)
    configuration.setNamingStrategy(new RailsNamingStrategy(new RailsNamingPolicy))

    for (resource <- ClassLoaders.getResources("META-INF/hibernate.cfg.xml", classOf[DdlGenerator]))
      configuration.configure(resource)
    val export = new SchemaExport(configuration)
    export.setOutputFile(fileName)
    export.execute(false, false, false, true)
  }
}

class DdlGenerator