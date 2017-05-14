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

import org.beangle.commons.bean.Factory
import org.beangle.commons.cdi.{ Container, ContainerListener }
import org.beangle.commons.lang.annotation.description
import org.hibernate.SessionFactory
import org.beangle.commons.model.meta.Domain
import org.beangle.data.hibernate.cfg.MappingService
import org.beangle.commons.collection.Collections

@description("基于Hibernate提供的元信息工厂")
class DomainsFactory extends ContainerListener {

  val domains = Collections.newMap[SessionFactory, Domain]

  override def onStarted(container: Container): Unit = {
    val factories = container.getBeans(classOf[SessionFactory]).values
    factories foreach { f =>
      val ms = f.getSessionFactoryOptions.getServiceRegistry.getService(classOf[MappingService])
      if (null != ms) {
        domains.put(f, ms.mappings.buildDomain())
      }
    }
  }
}