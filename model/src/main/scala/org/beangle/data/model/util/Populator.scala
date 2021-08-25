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

package org.beangle.data.model.util

import org.beangle.commons.collection.Collections
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.{EntityType, Property}

object Populator {

  class CopyResult {
    var fails: Map[String, String] = Map.empty

    def addFail(attr: String, cause: String): Unit = {
      fails += (attr -> cause)
    }
  }

}

/**
  * Populator interface.
  * @author chaostone
  */
trait Populator {
  /**
    * populate.
    */
  def populate(target: Entity[_], EntityType: EntityType, params: collection.Map[String, Any]): Populator.CopyResult

  /**
    *
    */
  def populate(target: Entity[_], EntityType: EntityType, attr: String, value: Any): Boolean

  /**
    * initProperty.
    */
  def init(target: Entity[_], t: EntityType, attr: String): (Any, Property)
}
