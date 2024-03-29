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

package org.beangle.data.orm

object NamingPolicy {
  /**
   * 表名最大长度
   */
  val DefaultMaxLength = 30
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
   * @param clazz
   * @param entityName
   */
  def classToTableName(clazz: Class[_], entityName: String): Name

  /**
   * Convert collection to table name
   *
   * @param clazz
   * @param entityName
   * @param tableName
   * @param collectionName
   */
  def collectionToTableName(clazz: Class[_], entityName: String, tableName: String, collectionName: String): Name

  def propertyToColumnName(clazz: Class[_], property: String): String
}

case class Name(schema: Option[String], text: String)
