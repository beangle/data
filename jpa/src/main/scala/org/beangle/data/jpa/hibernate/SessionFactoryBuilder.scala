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
package org.beangle.data.jpa.hibernate

import java.{ util => ju }

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.hibernate.{ SessionFactory, SessionFactoryObserver }
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.{ Configuration, NamingStrategy }
import org.hibernate.cfg.AvailableSettings.DATASOURCE

import javax.sql.DataSource

class SessionFactoryBuilder(val dataSource: DataSource, val configuration: Configuration) extends Logging {

  def build(): SessionFactory = {
    import org.hibernate.cfg.AvailableSettings._
    if (dataSource != null) configuration.getProperties.put(DATASOURCE, dataSource)
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
    logger.info(s"Building Hibernate SessionFactory in $watch")
    sessionFactory
  }
}

trait ConfigurableSessionFactory {

  def configLocations: Array[_]

  def persistLocations: Array[_]

  def namingStrategy: NamingStrategy

  def hibernateProperties: ju.Properties

  def configuration: Configuration

  def result: SessionFactory
}