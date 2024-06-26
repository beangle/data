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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.jdbc.ds.DataSourceUtils
import org.hibernate.dialect.H2Dialect

import java.util as ju
import javax.sql.DataSource

object Tests {

  def buildProperties(): ju.Properties = {
    val properties = new ju.Properties
    properties.put("hibernate.dialect", classOf[H2Dialect].getName)
    properties.put("hibernate.cache.use_second_level_cache", "true")
    properties.put("hibernate.javax.cache.provider", "org.ehcache.jsr107.EhcacheCachingProvider")
    properties.put("hibernate.hbm2ddl.auto", "create")
    properties.put("hibernate.show_sql", "true")
    properties.put("hibernate.jpa.metamodel.population", "disabled")
    properties
  }

  def buildTestH2(): DataSource = {
    val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass).get)
    DataSourceUtils.build("h2", properties("h2.username"), properties("h2.password"), Map("url" -> properties("h2.url")))
  }

  def buildTestPg(): DataSource = {
    val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass).get)
    DataSourceUtils.build("h2", properties("pg.username"), properties("pg.password"), Map("url" -> properties("pg.url")))
  }
}
