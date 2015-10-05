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
package org.beangle.data.transfer

import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.EntityType

/**
 * MultiEntityImporter class.
 *
 * @author chaostone
 */
class MultiEntityTransfer extends AbstractTransfer with EntityTransfer with Logging {

  protected var currentEntities = new collection.mutable.HashMap[String, AnyRef]

  val foreignerKeys = new collection.mutable.HashSet[String]
  addForeignedKeys("code")
  // [alias,entityType]
  protected val entityTypes = new collection.mutable.HashMap[String, EntityType]

  /**
   * transferItem.
   */
  override def transferItem() {
    // 在给定的值的范围内
    curData foreach { entry =>
      var value = entry._2
      // 处理空字符串并对所有的字符串进行trim
      value match {
        case s: String =>
          if (Strings.isBlank(s)) value = null;
          else value = Strings.trim(s);
        case _ =>
      }
      // 处理null值
      if (null != value) {
        //   if (value.equals(Model.NULL)) value = null;
        val key = entry._1
        val entity = getCurrent(key);
        val attr = processAttr(key);
        val entityName = getEntityName(key);
        val etype = entityMetadata.getType(entityName).get
        populateValue(entity.asInstanceOf[Entity[_]], etype, attr, value);
      }
    }
  }

  /**
   * Populate single attribute
   */
  protected def populateValue(entity: Entity[_], etype: EntityType, attr: String, value: Any): Unit = {
    // 当有深层次属性
    if (Strings.contains(attr, '.')) {
      if (null != foreignerKeys) {
        val foreigner = isForeigner(attr);
        // 如果是个外键,先根据parentPath生成新的外键实体。
        // 因此导入的是外键,只能有一个属性导入.
        if (foreigner) {
          val parentPath = Strings.substringBeforeLast(attr, ".");
          val propertyType = populator.init(entity, etype, parentPath);
          val property = propertyType._1
          property match {
            case e: Entity[_] =>
              if (e.persisted) {
                populator.populate(entity, etype, parentPath, null);
                populator.init(entity, etype, parentPath);
              }
            case _ =>
          }
        }
      }
    }

    if (!populator.populate(entity, etype, attr, value)) {
      transferResult.addFailure(descriptions.get(attr) + " data format error.", value);
    }
  }

  override def processAttr(attr: String): String = {
    return Strings.substringAfter(attr, ".");
  }

  protected def getEntityClass(attr: String): Class[_] = {
    return getEntityType(attr).entityClass;
  }

  protected def getEntityType(attr: String): EntityType = {
    val alias = Strings.substringBefore(attr, ".");
    entityTypes.get(alias).orElse(entityTypes.get(attr)).orNull
  }

  def addEntity(alias: String, entityClass: Class[_]) {
    entityMetadata.getType(entityClass) match {
      case Some(entityType) => entityTypes.put(alias, entityType)
      case None             => throw new RuntimeException("cannot find entity type for " + entityClass);
    }
  }

  def addEntity(alias: String, entityName: String): Unit = {
    entityMetadata.getType(entityName) match {
      case Some(entityType) => entityTypes.put(alias, entityType)
      case None             => throw new RuntimeException("cannot find entity type for " + entityName)
    }
  }

  protected def getEntityName(attr: String): String = {
    return getEntityType(attr).entityName
  }

  def getCurrent(attr: String): AnyRef = {
    val alias = Strings.substringBefore(attr, ".");
    var entity = currentEntities.get(alias).orNull
    if (null == entity) {
      entityTypes.get(alias) match {
        case Some(entityType) =>
          entity = entityType.newInstance()
          currentEntities.put(alias, entity)
          entity
        case None =>
          logger.error("Not register entity type for $alias")
          throw new IllegalFormatException("Not register entity type for " + alias, null)
      }
    }
    entity
  }

  override def dataName: String = {
    return "multi entity";
  }

  private def isForeigner(attr: String): Boolean = {
    val property = Strings.substringAfterLast(attr, ".");
    foreignerKeys.contains(property);
  }

  override def current_=(obj: AnyRef) {
    currentEntities = obj.asInstanceOf[collection.mutable.HashMap[String, AnyRef]]
  }

  override def current: AnyRef = {
    currentEntities
  }

  def addForeignedKeys(foreignerKey: String): Unit = {
    this.foreignerKeys += foreignerKey
  }
  protected override def beforeImportItem(): Unit = {
    this.currentEntities.clear()
  }
}
