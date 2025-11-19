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

import org.beangle.commons.bean.{Factory, Initializing}
import org.beangle.commons.lang.annotation.description
import org.beangle.data.orm.hibernate.ConfigurationBuilder
import org.hibernate.SessionFactory
import org.hibernate.cfg.ManagedBeanSettings
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.{BeanFactory, BeanFactoryAware}
import org.springframework.core.io.Resource

import java.util as ju
import javax.sql.DataSource

@description("构建Hibernate的会话工厂")
class LocalSessionFactoryBean(val dataSource: DataSource) extends Factory[SessionFactory], Initializing, BeanFactoryAware {

  var ormLocations: Array[Resource] = Array.empty

  var devMode: Boolean = false

  var properties = new ju.Properties

  var result: SessionFactory = _

  private var beanFactory: ConfigurableListableBeanFactory = _

  def init(): Unit = {
    if (null != beanFactory) {
      properties.put(ManagedBeanSettings.BEAN_CONTAINER, new SpringBeanContainer(beanFactory))
    }
    val cfgb = new ConfigurationBuilder(dataSource, properties)
    cfgb.ormLocations = ormLocations.toIndexedSeq.map(l => l.getURL)
    if (devMode) cfgb.enableDevMode()
    val config = cfgb.build()
    result = config.buildSessionFactory()
  }

  def setBeanFactory(beanFactory: BeanFactory): Unit = {
    this.beanFactory = beanFactory.asInstanceOf[ConfigurableListableBeanFactory]
  }

}
