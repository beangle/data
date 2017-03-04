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

import scala.collection.{ JavaConverters, mutable }

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.commons.model.meta.{ BasicType, Domain }
import org.beangle.commons.model.meta.{ EntityType, ImmutableDomain, Type }
import org.beangle.commons.model.meta.Domain.{ AssociationPropertyImpl, BasicPropertyImpl, CollectionPropertyImpl, EmbeddableTypeImpl, EntityTypeImpl, MapPropertyImpl, PropertyImpl }
import org.beangle.data.hibernate.cfg.MappingService
import org.hibernate.{ Metamodel, SessionFactory }

import javax.persistence.metamodel.{ EmbeddableType => JpaEmbeddableType, EntityType => JpaEntityType, MapAttribute, PluralAttribute, SingularAttribute, Type => JpaType }
import javax.persistence.metamodel.Type.PersistenceType
import scala.collection.JavaConverters.asScalaSet

class DomainBuilder(factories: Iterable[SessionFactory]) extends Logging {
  val entities = new mutable.HashMap[String, EntityType]
  def build(): Domain = {
    for (factory <- factories) {
      val watch = new Stopwatch(true)
      val mappingService = factory.getSessionFactoryOptions.getServiceRegistry.getService(classOf[MappingService])
      entities ++= mappingService.mappings.entities
      val metamodel = factory.getMetamodel
      import collection.JavaConverters.asScalaSet
      for (et <- asScalaSet(metamodel.getEntities)) {
        if (!entities.contains(et.getName)) {
          entities.put(et.getName, buildEntity(metamodel, et))
        }
      }
    }
    new ImmutableDomain(entities.toMap)
  }

  private def buildEntity(metamodel: Metamodel, et: JpaEntityType[_]): EntityType = {
    var entityType = entities.get(et.getName).orNull
    if (null == entityType) {
      val em = new EntityTypeImpl(et.getJavaType, et.getName)
      for (attr <- asScalaSet(et.getAttributes)) {
        val p: PropertyImpl =
          if (attr.isAssociation) {
            val aet = attr.get.asInstanceOf[JpaEntityType[_]]
            new AssociationPropertyImpl(attr.getName, attr.getJavaType, buildEntity(metamodel, aet))
          } else if (attr.isCollection) {
            buildCollection(metamodel, attr.asInstanceOf[PluralAttribute[_, _, _]])
          }else if(attr.getDeclaringType
          else {
            new BasicPropertyImpl(attr.getName, attr.getJavaType)
          }
        attr match {
          case sa: SingularAttribute[_, _] =>
            p.optional = sa.isOptional
            if (sa.isId) em.id = p
          case _ =>
        }
        em.addProperty(p)
      }
    }
    entityType
  }

  private def buildType(metamodel: Metamodel, typ: JpaType[_]): Type = {
    typ.getPersistenceType match {
      case PersistenceType.BASIC =>
        new BasicType(typ.getJavaType)
      case PersistenceType.ENTITY =>
        buildEntity(metamodel, typ.asInstanceOf[JpaEntityType[_]])
      case PersistenceType.EMBEDDABLE =>
        buildEmbeddable(metamodel, typ.asInstanceOf[JpaEmbeddableType[_]])
    }
  }

  private def buildCollection(metamodel: Metamodel, attr: PluralAttribute[_, _, _]): PropertyImpl = {
    attr match {
      case ma: MapAttribute[_, _, _] =>
        new MapPropertyImpl(attr.getName, attr.getJavaType, buildType(metamodel, ma.getKeyType), buildType(metamodel, attr.getElementType))
      case pa: PluralAttribute[_, _, _] =>
        new CollectionPropertyImpl(attr.getName, attr.getJavaType, buildType(metamodel, attr.getElementType))
    }
  }

  private def buildEmbeddable(metamodel: Metamodel, et: JpaEmbeddableType[_]): EmbeddableTypeImpl = {
    val ep = new EmbeddableTypeImpl(et.getJavaType)
    for (attr <- asScalaSet(et.getAttributes)) {
      val p: PropertyImpl =
        if (attr.isAssociation) {
          val aet = attr.getDeclaringType.asInstanceOf[JpaEntityType[_]]
          new AssociationPropertyImpl(attr.getName, attr.getJavaType, buildEntity(metamodel, aet))
        } else if (attr.isCollection) {
          buildCollection(metamodel, attr.asInstanceOf[PluralAttribute[_, _, _]])
        } else {
          new BasicPropertyImpl(attr.getName, attr.getJavaType)
        }
      attr match {
        case sa: SingularAttribute[_, _] =>
          p.optional = sa.isOptional
        case _ =>
      }
      ep.addProperty(p)
    }
    ep
  }
}
