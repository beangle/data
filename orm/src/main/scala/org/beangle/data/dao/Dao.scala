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

package org.beangle.data.dao

import org.beangle.data.model.Entity

/** Dao trait
  * @author chaostone
  */
trait Dao[T <: Entity[ID], ID] {

  /**
    * get T by id.
    */
  def get(id: ID): T

  /**
    * find T by id.
    */
  def find(id: ID): Option[T]

  /**
    * search T by id.
    */
  def find(first: ID, ids: ID*): Seq[T]

  /**
    * save or update entities
    */
  def saveOrUpdate(first: T, entities: T*): Unit

  /**
    * save or update entities
    */
  def saveOrUpdate(entities: Seq[T]): Unit

  /**
    * remove entities.
    */
  def remove(entities: Seq[T]): Unit

  /**
    * remove entities.
    */
  def remove(first: T, others: T*): Unit

  /**
    * remove entities by id
    */
  def remove(id: ID, ids: ID*): Unit

  /**
    * get entity type
    */
  def entityClass: Class[T]

}
