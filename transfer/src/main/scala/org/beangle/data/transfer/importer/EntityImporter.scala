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
package org.beangle.data.transfer.importer

import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.{ Domain, EntityType }
import org.beangle.data.model.util.Populator
import org.beangle.data.transfer.IllegalFormatException

/**
 * EntityImporter interface.
 *
 * @author chaostone
 */
trait EntityImporter extends Importer {

  def foreignerKeys: collection.Set[String]

  def addForeignedKeys(foreignerKey: String)

  var populator: Populator = _

  var domain: Domain = _

}

/**
 * MultiEntityImporter class.
 *
 * @author chaostone
 */
class MultiEntityImporter extends AbstractImporter with EntityImporter with Logging {

  protected var currents = new collection.mutable.HashMap[String, AnyRef]

  val foreignerKeys = new collection.mutable.HashSet[String]

  addForeignedKeys("code")
  // [alias,entityType]
  protected val entityTypes = new collection.mutable.HashMap[String, EntityType]

  /**
   * 摘取指定前缀的参数
   */
  private def sub(data: collection.Map[String, Any], alias: String): collection.mutable.Map[String, Any] = {
    val prefix = alias + "."
    val newParams = new collection.mutable.HashMap[String, Any]
    for ((key, value) <- data) {
      if (key.indexOf(prefix) == 0) {
        newParams.put(key.substring(prefix.length), value)
      }
    }
    newParams
  }
  /**
   * transferItem.
   */
  override def transferItem() {
    entityTypes foreach {
      case (name, etype) =>
        val entity = getCurrent(name)
        sub(curData, name) foreach { entry =>
          var value = entry._2
          // 处理空字符串并对所有的字符串进行trim
          value match {
            case s: String =>
              if (Strings.isBlank(s)) value = null
              else value = Strings.trim(s)
            case _ =>
          }
          // 处理null值
          if (null != value) {
            if (value.equals("null")) value = null
            populateValue(entity.asInstanceOf[Entity[_]], etype, entry._1, value)
          }
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
        val foreigner = isForeigner(attr)
        // 如果是个外键,先根据parentPath生成新的外键实体,因此导入的是外键,只能有一个属性导入.
        if (foreigner) {
          val parentPath = Strings.substringBeforeLast(attr, ".")
          val propertyType = populator.init(entity, etype, parentPath)
          val property = propertyType._1
          property match {
            case e: Entity[_] =>
              if (e.persisted) {
                populator.populate(entity, etype, parentPath, null)
                populator.init(entity, etype, parentPath)
              }
            case _ =>
          }
        }
      }
    }

    if (!populator.populate(entity, etype, attr, value)) {
      transferResult.addFailure(descriptions.get(attr) + " data format error.", value)
    }
  }

  protected def getEntityClass(alias: String): Class[_] = {
    getEntityType(alias).clazz
  }

  protected def getEntityType(alias: String): EntityType = {
    entityTypes(alias)
  }

  def addEntity(clazz: Class[_]) {
    val shortName = Strings.uncapitalize(Strings.substringAfterLast(clazz.getName, "."))
    this.addEntity(shortName, clazz)
  }

  def addEntity(alias: String, entityClass: Class[_]) {
    domain.getEntity(entityClass) match {
      case Some(entityType) => entityTypes.put(alias, entityType)
      case None             => throw new RuntimeException("cannot find entity type for " + entityClass)
    }
  }

  def addEntity(alias: String, entityName: String): Unit = {
    domain.getEntity(entityName) match {
      case Some(entityType) => entityTypes.put(alias, entityType)
      case None             => throw new RuntimeException("cannot find entity type for " + entityName)
    }
  }

  protected def getEntityName(attr: String): String = {
    return getEntityType(attr).entityName
  }

  def getCurrent(alias: String): AnyRef = {
    var entity = currents.get(alias).orNull
    if (null == entity) {
      entityTypes.get(alias) match {
        case Some(entityType) =>
          entity = entityType.newInstance()
          currents.put(alias, entity)
          entity
        case None =>
          logger.error("Not register entity type for $alias")
          throw new IllegalFormatException("Not register entity type for " + alias, null)
      }
    }
    entity
  }

  override def dataName: String = {
    "multi entity"
  }

  private def isForeigner(attr: String): Boolean = {
    val property = Strings.substringAfterLast(attr, ".")
    foreignerKeys.contains(property)
  }

  override def current_=(obj: AnyRef) {
    currents = obj.asInstanceOf[collection.mutable.HashMap[String, AnyRef]]
  }

  override def current: AnyRef = {
    currents
  }

  def addForeignedKeys(foreignerKey: String): Unit = {
    this.foreignerKeys += foreignerKey
  }

  protected override def beforeImportItem(): Unit = {
    this.currents.clear()
  }
}

class DefaultEntityImporter(val entityClass: Class[_], val shortName: String) extends MultiEntityImporter {

  this.prepare = EntityPrepare

  protected override def getEntityType(attr: String): EntityType = {
    return entityTypes(shortName)
  }

  def getEntityClass: Class[_] = {
    return entityTypes(shortName).clazz
  }

  def getEntityName(): String = {
    return entityTypes(shortName).entityName
  }

  override def getCurrent(alias: String): AnyRef = {
    current
  }

  override def current: AnyRef = {
    super.getCurrent(shortName)
  }

  protected override def getEntityName(alias: String): String = {
    getEntityName()
  }

  override def current_=(obj: AnyRef) = {
    currents.put(shortName, obj)
  }

}
