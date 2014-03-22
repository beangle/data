/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.model.dao

import org.beangle.commons.lang.Arrays
import scala.collection.mutable.ListBuffer
/**
 * Operation class.
 *
 * @author chaostone
 */
object Operation {
  case class Type protected[dao] () {}
  val SaveUpdate = new Type()
  val Remove = new Type()

  def apply(t: Operation.Type, data: AnyRef) = new Operation(t, data)

  class Builder {
    private val operations = new ListBuffer[Operation]

    def saveOrUpdate(entities: Seq[AnyRef]): this.type = {
      if (!entities.isEmpty) {
        for (entity <- entities) {
          if (null != entity) operations += Operation(SaveUpdate, entity)
        }
      }
      this
    }

    def saveOrUpdate(first: AnyRef, entities: AnyRef*): this.type = {
      operations += Operation(SaveUpdate, first)
      for (entity <- entities) {
        if (null != entity) operations += Operation(SaveUpdate, entity)
      }
      this
    }

    def remove(entities: Seq[AnyRef]): this.type = {
      if (!entities.isEmpty) {
        for (entity <- entities) {
          if (null != entity) operations += Operation(Remove, entity)
        }
      }
      this
    }

    def remove(first: AnyRef, entities: AnyRef*): this.type = {
      operations += Operation(Remove, first)
      for (entity <- entities) {
        if (null != entity) operations += Operation(Remove, entity)
      }
      this
    }

    def build(): List[Operation] = operations.toList
  }

  /**
   * <p>
   * saveOrUpdate.
   * </p>
   *
   * @param entities a {@link java.util.Collection} object.
   * @return a {@link org.beangle.commons.dao.Operation.Builder} object.
   */
  def saveOrUpdate(entities: Seq[_]): Builder = new Builder().saveOrUpdate(entities)

  /**
   * <p>
   * saveOrUpdate.
   * </p>
   *
   * @param entities a {@link java.lang.Object} object.
   * @return a {@link org.beangle.commons.dao.Operation.Builder} object.
   */
  def saveOrUpdate(first: AnyRef, entities: AnyRef*): Builder = new Builder().saveOrUpdate(first, entities: _*)

  /**
   * <p>
   * remove.
   * </p>
   *
   * @param entities a {@link java.util.Collection} object.
   * @return a {@link org.beangle.commons.dao.Operation.Builder} object.
   */
  def remove(entities: Seq[_]): Builder = new Builder().remove(entities)

  /**
   * <p>
   * remove.
   * </p>
   *
   * @param entities a {@link java.lang.Object} object.
   * @return a {@link org.beangle.commons.dao.Operation.Builder} object.
   */
  def remove(first: AnyRef, entities: AnyRef*): Builder = new Builder().remove(first, entities)

}
class Operation(val t: Operation.Type, val data: AnyRef) {

}
