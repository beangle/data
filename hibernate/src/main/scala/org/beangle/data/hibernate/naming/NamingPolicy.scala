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
package org.beangle.data.hibernate.naming

object NamingPolicy {
  /**
   * 表名最大长度
   */
  val DefaultMaxLength = 30

  var Instance: NamingPolicy = _
}
/**
 * Entity table and Collection Table Naming Strategy.
 *
 * @author chaostone
 */
trait NamingPolicy {

  /**
   * Convert class to table name
   *
   * @param className
   */
  def classToTableName(className: String): String

  /**
   * Convert collection to table name
   *
   * @param className
   * @param tableName
   * @param collectionName
   */
  def collectionToTableName(className: String, tableName: String, collectionName: String): String

  /**
   * Return schema for package
   */
  def getSchema(clazz: Class[_]): Option[String]

  /**
   * Return schema for package
   */
  def getSchema(entityName: String): Option[String]

  /**
   * Mapped in multischema?
   *
   */
  def multiSchema: Boolean

}
