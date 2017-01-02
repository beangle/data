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

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.hibernate.cfg.OverrideConfiguration
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

  def ormLocations: Array[_]

  var configurationClass: Class[_ <: Configuration] = classOf[OverrideConfiguration]

  var namingStrategy: NamingStrategy = _

  var hibernateProperties = new ju.Properties

  /**For display informations*/
  var configuration: Configuration = _

  var result: SessionFactory = _

}