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

import scala.collection.mutable.ListBuffer
/**
 * Operation class.
 *
 * @author chaostone
 */
object Operation{

  class Builder {
    private val operations = new ListBuffer[Operation]

    def saveOrUpdate(entities: AnyRef*): this.type = {
      for (entity <- entities) {
        entity match {
          case null           =>
          case c: Iterable[_] => c foreach (e => operations += Operation(OperationType.SaveUpdate, e))
          case _              => operations += Operation(OperationType.SaveUpdate, entity)
        }
      }
      this
    }

    def remove(entities: AnyRef*): this.type = {
      for (entity <- entities) {
        if (null != entity) {
          entity match {
            case null           =>
            case c: Iterable[_] => c foreach (e => operations += Operation(OperationType.Remove, e))
            case _              => operations += Operation(OperationType.Remove, entity)
          }
        }
      }
      this
    }

    def build(): List[Operation] = operations.toList
  }

  def saveOrUpdate(entities: AnyRef*): Builder = new Builder().saveOrUpdate(entities)

  def remove(entities: AnyRef*): Builder = new Builder().remove(entities)

}

case class Operation(val typ: OperationType, val data: Any)

enum OperationType{
  case  SaveUpdate, Remove

}
