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

import org.beangle.commons.bean.Factory
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.annotation.description
import org.beangle.commons.lang.reflect.{BeanInfos, Reflections}
import org.beangle.data.model.meta.{Domain, EntityType, ImmutableDomain}
import org.beangle.data.orm.hibernate.cfg.MappingService
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor

import java.lang.reflect.Field

object DomainFactory {

  def build(factory: SessionFactory): Domain = {
    build(List(factory))
  }

  def build(factories: Iterable[SessionFactory]): Domain = {
    val entities = Collections.newSet[EntityType]
    factories foreach { f =>
      val rm = f.asInstanceOf[SessionFactoryImplementor].getRuntimeMetamodels

      val ms = f.getSessionFactoryOptions.getServiceRegistry.getService(classOf[MappingService])
      var field: Option[Field] = null
      if (null != ms) {
        val newEntities = ms.mappings.entityTypes.values
        newEntities foreach { entity =>
          val pf = rm.getMappingMetamodel.getEntityDescriptor(entity.clazz).getRepresentationStrategy.getProxyFactory
          if null == field then field = Reflections.getField(pf.getClass, "proxyClass")
          field foreach { f =>
            BeanInfos.cache.update(f.get(pf).asInstanceOf[Class[_]], BeanInfos.get(entity.clazz))
          }
        }
        entities ++= newEntities
      }
    }
    ImmutableDomain(entities)
  }
}

@description("基于Hibernate提供的元信息工厂")
class DomainFactory(factories: Iterable[SessionFactory]) extends Factory[Domain] {
  val result: Domain = DomainFactory.build(factories)
}
