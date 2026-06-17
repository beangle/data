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

package org.beangle.data.hibernate

import org.beangle.commons.bean.{Factory, Initializing}
import org.beangle.commons.cdi.Container
import org.beangle.commons.config.{Environment, MutableEnvironment, XmlConfigs}
import org.beangle.commons.lang.annotation.description
import org.beangle.data.hibernate.ConfigurationBuilder
import org.hibernate.SessionFactory
import org.hibernate.cfg.ManagedBeanSettings
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

import java.util as ju
import javax.sql.DataSource

@description("构建Hibernate的会话工厂")
class LocalSessionFactoryBean(val dataSource: DataSource) extends Factory[SessionFactory], Initializing {

  var ormLocation: String = "classpath*:beangle.xml"

  var properties = new ju.Properties

  private var result: SessionFactory = _

  var container: Container = _

  def init(): Unit = {
    if (null != container && container.underlying != null) {
      val bf = container.underlying.asInstanceOf[ConfigurableListableBeanFactory]
      properties.put(ManagedBeanSettings.BEAN_CONTAINER, new SpringBeanContainer(bf))
    }
    val env = getEnvironment
    val configs = getXmlConfigs
    val cfgb = new ConfigurationBuilder(dataSource, env, configs.load(ormLocation), properties)
    if (Environment.isDevMode) cfgb.enableDevMode()
    val config = cfgb.build()
    result = config.buildSessionFactory()
  }

  private def getXmlConfigs: XmlConfigs = {
    if (null == container) new XmlConfigs
    else container.getBean(classOf[XmlConfigs]).getOrElse(new XmlConfigs)
  }

  private def getEnvironment: Environment = {
    if (null == container) MutableEnvironment.system
    else container.getBean(classOf[Environment]).getOrElse(MutableEnvironment.system)
  }

  override def getObject: SessionFactory = result

}
