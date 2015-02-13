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
package org.beangle.data.jpa.hibernate

import java.{ util => ju }
import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable
import org.beangle.commons.bean.Factory
import org.beangle.commons.inject.Container
import org.beangle.commons.lang.annotation.description
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.model.meta.{ CollectionType, ComponentType, DefaultEntityMetadata, EntityMetadata, EntityType, IdentifierType, Type }
import org.hibernate.SessionFactory
import org.hibernate.`type`.{ MapType, SetType }
import org.hibernate.{ `type` => htype }
import org.beangle.commons.inject.ContainerRefreshedHook
import org.beangle.data.model.Entity

@description("基于Hibernate提供的元信息工厂")
class HibernateMetadataFactory extends Factory[EntityMetadata] with ContainerRefreshedHook {

  var result: EntityMetadata = null

  override def notify(container: Container): Unit = {
    result = new EntityMetadataBuilder(container.getBeans(classOf[SessionFactory]).values).build()
  }
}

//TODO add test by xml or annotation configuration
class EntityMetadataBuilder(factories: Iterable[SessionFactory]) extends Logging {
  /** entity-name->entity-type */
  val entityTypes = new mutable.HashMap[String, EntityType]
  val collectionTypes = new mutable.HashMap[String, CollectionType]

  def build(): EntityMetadata = {
    for (factory <- factories) {
      val watch = new Stopwatch(true)
      val classMetadatas = factory.getAllClassMetadata
      val entityCount = entityTypes.size
      val collectionCount = collectionTypes.size
      for (entry <- classMetadatas.entrySet)
        buildEntityType(factory, entry.getValue.getEntityName)

      collectionTypes.clear()
      debug(s"Find ${entityTypes.size - entityCount} entities,${collectionTypes.size - collectionCount} collections from hibernate in ${watch}")
    }
    new DefaultEntityMetadata(entityTypes.values)
  }
  /**
   * 按照实体名，构建或者查找实体类型信息.<br>
   * 调用后，实体类型则存放与entityTypes中.
   */
  private def buildEntityType(factory: SessionFactory, entityName: String): EntityType = {
    var entityType = entityTypes.get(entityName).orNull
    if (null == entityType) {
      val cm = factory.getClassMetadata(entityName)
      if (null == cm) {
        error(s"Cannot find classMetadata for $entityName")
        return null
      }
      entityType = new EntityType(cm.getMappedClass, cm.getEntityName, cm.getIdentifierPropertyName)
      entityTypes.put(cm.getEntityName, entityType)
      val propertyTypes = new mutable.HashMap[String, Type]
      for (pname <- cm.getPropertyNames) {
        val htype = cm.getPropertyType(pname)
        if (htype.isEntityType) {
          propertyTypes.put(pname, buildEntityType(factory, htype.getName))
        } else if (htype.isComponentType) {
          propertyTypes.put(pname, buildComponentType(factory, entityName, pname))
        } else if (htype.isCollectionType) {
          propertyTypes.put(pname,
            buildCollectionType(factory, defaultCollectionClass(htype), entityName + "." + pname))
        } else {
          propertyTypes.put(pname, new IdentifierType(htype.getReturnedClass))
        }
      }
      propertyTypes.put(cm.getIdentifierPropertyName, new IdentifierType(cm.getIdentifierType.getReturnedClass))
      entityType.propertyTypes = propertyTypes.toMap
    }
    entityType
  }

  private def buildCollectionType(factory: SessionFactory, collectionClass: Class[_], role: String): CollectionType = {
    val cm = factory.getCollectionMetadata(role)
    if (null == cm) return null
    val etype = cm.getElementType
    val elementType =
      if (etype.isEntityType) entityTypes.get(etype.getName).getOrElse(buildEntityType(factory, etype.getName))
      else new IdentifierType(etype.getReturnedClass)

    val collectionType = new CollectionType(collectionClass, elementType)
    if (!collectionTypes.contains(collectionType.name)) collectionTypes.put(collectionType.name, collectionType)

    collectionType
  }

  private def buildComponentType(factory: SessionFactory, entityName: String, propertyName: String): ComponentType = {
    var result: ComponentType = null
    entityTypes.get(entityName) foreach { t =>
      result = t.propertyTypes.get(propertyName).orNull.asInstanceOf[ComponentType]
    }

    if (null == result) {
      val cm = factory.getClassMetadata(entityName)
      val hcType = cm.getPropertyType(propertyName).asInstanceOf[htype.ComponentType]
      val propertyNames = hcType.getPropertyNames

      val propertyTypes = new mutable.HashMap[String, Type]
      var j = 0
      while (j < propertyNames.length) {
        val pName = propertyNames(j)
        val etype = cm.getPropertyType(propertyName + "." + pName)
        if (etype.isEntityType) {
          propertyTypes.put(pName, buildEntityType(factory, etype.getName))
        } else if (etype.isComponentType) {
          propertyTypes.put(pName, buildComponentType(factory, entityName, propertyName + "." + pName))
        } else if (etype.isCollectionType) {
          propertyTypes.put(pName,
            buildCollectionType(factory, defaultCollectionClass(etype), entityName + "." + propertyName + "." + pName))
        }else {
          propertyTypes.put(pName, new IdentifierType(etype.getReturnedClass))
        }
        j += 1
      }
      result = new ComponentType(hcType.getReturnedClass, propertyTypes.toMap)
    }
    result
  }

  private def defaultCollectionClass(collectionType: htype.Type): Class[_] = {
    if (collectionType.isAnyType) {
      null
    } else if (classOf[htype.SetType].isAssignableFrom(collectionType.getClass)) {
      classOf[ju.HashSet[_]]
    } else if (classOf[htype.MapType].isAssignableFrom(collectionType.getClass)) {
      classOf[ju.HashMap[_, _]]
    } else {
      classOf[ju.ArrayList[_]]
    }
  }
}
