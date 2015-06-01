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
package org.beangle.data.jpa.hibernate

import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.description
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.AssertionFailure
import org.hibernate.cfg.NamingStrategy

/**
 * 类似Rails的数据库表名、列名命名策略
 */
@description("类似Rails的数据库表名、列名命名策略")
@SerialVersionUID(-2656604564223895758L)
class RailsNamingStrategy(val policy: NamingPolicy) extends NamingStrategy with Logging with Serializable {

  NamingPolicy.Instance = policy

  /**
   * 根据实体名(entityName)命名表
   */
  override def classToTableName(className: String): String = {
    val tableName = policy.classToTableName(className)
    if (tableName.length > NamingPolicy.DefaultMaxLength) logger.warn(s"$tableName's length greate than 30!")
    logger.debug(s"Mapping entity[$className] to $tableName")
    tableName
  }

  /**
   * 对自动起名和使体内集合配置的表名，添加前缀
   *
   * <pre>
   * 配置好的实体表名和关联表的名字都会经过此方法。
   * </re>
   */
  override def tableName(tableName: String): String = tableName

  /** 对配置文件起好的列名,不进行处理 */
  override def columnName(columnName: String): String = columnName

  /**
   * 数据列的逻辑名
   *
   * <pre>
   * 如果有列名，不做处理，否则按照属性自动起名.
   * 该策略保证columnName=logicalColumnName
   * </pre>
   */
  override def logicalColumnName(columnName: String, propertyName: String): String = {
    if (Strings.isNotEmpty(columnName)) columnName else propertyToColumnName(propertyName)
  }

  /**
   * 根据属性名自动起名
   *
   * <pre>
   * 将混合大小写，带有.分割的属性描述，转换成下划线分割的名称。
   * 属性名字包括：简单属性、集合属性、组合属性(component.name)
   * </pre>
   */
  override def propertyToColumnName(propertyName: String): String = {
    if (isManyToOne) addUnderscores(unqualify(propertyName)) + "_id"
    else addUnderscores(unqualify(propertyName))
  }

  /** Return the argument */
  override def joinKeyColumnName(joinedColumn: String, joinedTable: String): String = columnName(joinedColumn)

  /** Return the property name or propertyTableName */
  override def foreignKeyColumnName(propertyName: String, propertyEntityName: String,
    propertyTableName: String, referencedColumnName: String): String = {
    var header = if (null == propertyName) propertyTableName else unqualify(propertyName)
    if (header == null) { throw new AssertionFailure("NamingStrategy not properly filled") }
    header = if (isManyToOne) addUnderscores(header) else addUnderscores(propertyTableName)
    return header + "_" + referencedColumnName

  }

  /** Collection Table */
  override def collectionTableName(ownerEntity: String, ownerEntityTable: String, associatedEntity: String,
    associatedEntityTable: String, propertyName: String): String = {
    var ownerTable: String = null
    // Just for annotation configuration,it's ownerEntity is classname(not entityName), and
    // ownerEntityTable is class shortname
    if (Character.isUpperCase(ownerEntityTable.charAt(0))) {
      ownerTable = policy.classToTableName(ownerEntity)
    } else {
      ownerTable = tableName(ownerEntityTable)
    }
    val tblName = policy.collectionToTableName(ownerEntity, ownerTable, propertyName)
    if (tblName.length() > NamingPolicy.DefaultMaxLength) logger.warn(s"$tblName's length greate than 30!")
    tblName
  }

  /**
   * Returns either the table name if explicit or if there is an associated
   * table, the concatenation of owner entity table and associated table
   * otherwise the concatenation of owner entity table and the unqualified
   * property name
   */
  override def logicalCollectionTableName(tableName: String, ownerEntityTable: String,
    associatedEntityTable: String, propertyName: String): String = {
    if (tableName == null) {
      // use of a stringbuilder to workaround a JDK bug
      new StringBuilder(ownerEntityTable).append("_")
        .append(if (associatedEntityTable == null) unqualify(propertyName) else associatedEntityTable).toString()
    } else {
      tableName
    }
  }

  /**
   * Return the column name if explicit or the concatenation of the property
   * name and the referenced column
   */
  override def logicalCollectionColumnName(columnName: String, propertyName: String, referencedColumn: String): String = {
    if (Strings.isNotEmpty(columnName)) columnName else (unqualify(propertyName) + "_" + referencedColumn)
  }

  final def addUnderscores(name: String): String = Strings.unCamel(name.replace('.', '_'), '_')

  final def unqualify(qualifiedName: String): String = {
    val loc = qualifiedName.lastIndexOf('.')
    if (loc < 0) qualifiedName else qualifiedName.substring(loc + 1)
  }
  /**
   * 检查是否为ManyToOne调用
   */
  private def isManyToOne: Boolean = {
    val trace = Thread.currentThread().getStackTrace()
    var matched = false
    if (trace.length >= 9) {
      matched = (2 to 8) exists { i =>
        ("bindManyToOne" == trace(i).getMethodName() || trace(i).getClassName().equals("org.hibernate.cfg.ToOneFkSecondPass"))
      }
    }
    matched
  }
}
