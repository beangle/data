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
package org.beangle.data.model.util

import org.beangle.commons.collection.Collections
import org.beangle.data.model.meta.{EntityType, Property}

final class SimpleEntityType(val clazz: Class[_]) extends EntityType {

  var entityName: String = clazz.getName

  var properties: collection.mutable.Map[String, Property] = Collections.newMap

  def id: Property = {
    properties("id")
  }

  def property(name: String): Property = {
    properties(name)
  }

  def getProperty(property: String): Option[Property] = {
    properties.get(property)
  }

  def addProperty(property: Property): Unit = {
    properties.put(property.name, property)
  }
}