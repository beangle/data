/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
import scala.collection.mutable
import scala.collection.JavaConversions._
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.model.meta.CollectionType
import org.beangle.data.model.meta.ComponentType
import org.beangle.data.model.meta.DefaultEntityMetadata
import org.beangle.data.model.meta.EntityType
import org.beangle.data.model.meta.IdentifierType
import org.beangle.data.model.meta.EntityMetadata
import org.beangle.data.model.meta.Type
import org.hibernate.SessionFactory
import org.hibernate.{ `type` => htype }
import java.{ util => ju }

object EntityMetadataBuilder {
  def apply(factories: Iterable[SessionFactory]): EntityMetadata = new EntityMetadataBuilder().build(factories)
}

private[hibernate] class EntityMetadataBuilder extends Logging {
  /** entity-name->entity-type */
  val entityTypes = new mutable.HashMap[String, EntityType]
  val collectionTypes = new mutable.HashMap[String, CollectionType]

  def build(factories: Iterable[SessionFactory]): EntityMetadata = {
    require(null != factories)
    for (factory <- factories) {
      val watch = new Stopwatch(true)
      val classMetadatas = factory.getAllClassMetadata
      val entityCount = entityTypes.size
      val collectionCount = collectionTypes.size
      for (entry <- classMetadatas.entrySet)
        buildEntityType(factory, entry.getValue.getEntityName)

      logger.info("Find {} entities,{} collections from hibernate in {}", Array(
        entityTypes.size - entityCount, collectionTypes.size - collectionCount, watch))
      collectionTypes.clear()
    }
    new DefaultEntityMetadata(entityTypes.values)
  }
  /**
   * 按照实体名，构建或者查找实体类型信息.<br>
   * 调用后，实体类型则存放与entityTypes中.
   *
   * @param entityName
   */
  private def buildEntityType(factory: SessionFactory, entityName: String): EntityType = {
    val entityType = entityTypes.get(entityName).orNull
    if (null == entityType) {
      val cm = factory.getClassMetadata(entityName)
      if (null == cm) {
        logger.error("Cannot find classMetadata for {}", entityName)
        return null
      }
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
        }
      }
      propertyTypes.put(cm.getIdentifierPropertyName, new IdentifierType(cm.getIdentifierType.getReturnedClass))
      entityTypes.put(cm.getEntityName, new EntityType(cm.getMappedClass, cm.getEntityName, cm.getIdentifierPropertyName, propertyTypes.toMap))
    }
    entityType
  }

  private def buildCollectionType(factory: SessionFactory, collectionClass: Class[_], role: String): CollectionType = {
    val cm = factory.getCollectionMetadata(role)
    if (null == cm) return null
    val etype = cm.getElementType
    val elementType =
      if (etype.isEntityType) {
        entityTypes.get(etype.getName).getOrElse(buildEntityType(factory, etype.getName))
      } else {
        new IdentifierType(etype.getReturnedClass)
      }

    val collectionType = new CollectionType(collectionClass, elementType)
    if (!collectionTypes.contains(collectionType.name)) {
      collectionTypes.put(collectionType.name, collectionType)
    }
    return collectionType
  }

  private def buildComponentType(factory: SessionFactory, entityName: String, propertyName: String): ComponentType = {
    var result: ComponentType = null
    entityTypes.get(entityName) foreach { t =>
      result = t.propertyTypes.get(propertyName).orNull.asInstanceOf[ComponentType]
    }

    if (null == result) {
      val cm = factory.getClassMetadata(entityName)
      val hcType = cm
        .getPropertyType(propertyName).asInstanceOf[htype.ComponentType]
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