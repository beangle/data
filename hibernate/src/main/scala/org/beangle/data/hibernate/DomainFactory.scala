/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate

import org.beangle.commons.bean.Factory
import org.beangle.cdi.{ Container, ContainerListener }
import org.beangle.commons.lang.annotation.description
import org.hibernate.SessionFactory
import org.beangle.data.model.meta.Domain
import org.beangle.data.hibernate.cfg.MappingService
import org.beangle.commons.collection.Collections
import org.beangle.data.model.meta.ImmutableDomain
import org.beangle.data.model.meta.EntityType

object DomainFactory {

  def build(factory: SessionFactory): Domain = {
    build(List(factory))
  }

  def build(factories: Iterable[SessionFactory]): Domain = {
    var entities = Collections.newSet[EntityType]
    factories foreach { f =>
      val ms = f.getSessionFactoryOptions.getServiceRegistry.getService(classOf[MappingService])
      if (null != ms) {
        entities ++= ms.mappings.entities.values
      }
    }
    ImmutableDomain(entities)
  }
}
@description("基于Hibernate提供的元信息工厂")
class DomainFactory extends ContainerListener with Factory[Domain] {

  var result: Domain = _

  override def onStarted(container: Container): Unit = {
    result = DomainFactory.build(container.getBeans(classOf[SessionFactory]).values)
  }

}
