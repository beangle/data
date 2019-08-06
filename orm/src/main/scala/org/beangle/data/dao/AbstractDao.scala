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
package org.beangle.data.dao

import org.beangle.data.model.Entity

abstract class AbstractDao[T <: Entity[ID], ID <: java.io.Serializable](val entityClass: Class[T], val entityDao: EntityDao) extends Dao[T, ID] {

  /** get T by id.
    */
  def get(id: ID): T = entityDao.get(entityClass, id)

  /** search T by id.
    */
  def find(id: ID): Option[T] = entityDao.find(entityClass, id)

  /** search T by id.
    */
  def find(ids: Array[ID]): Seq[T] = entityDao.find(entityClass, ids)

  /** save or update entities
    */
  def saveOrUpdate(first: T, entities: T*): Unit = {
    entityDao.saveOrUpdate(first, entities: _*)
  }

  /** save or update entities
    */
  def saveOrUpdate(entities: Seq[T]): Unit = {
    entityDao.saveOrUpdate(entities)
  }

  /** remove entities.
    */
  def remove(entities: Iterable[T]): Unit = {
    entityDao.remove(entities)
  }

  /** remove entities.
    */
  def remove(first: T, entities: T*): Unit = {
    entityDao.remove(first, entities: _*)
  }

  /** remove entities by id
    */
  def remove(id: ID, ids: ID*): Unit = {
    entityDao.remove(entityClass, id, ids: _*)
  }

}
