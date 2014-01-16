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
package org.beangle.data.model.meta

import org.beangle.commons.bean.PropertyUtils

/**
 * <p>
 * ComponentType class.
 * </p>
 *
 * @author chaostone
 */
class ComponentType(val componentClass: Class[_]) extends AbstractType {

  var propertyTypes: Map[String, Type] = Map()

  override def isComponentType = true

  override def name = componentClass.toString

  override def returnedClass = componentClass

  /**
   * Get the type of a particular (named) property
   */
  override def getPropertyType(propertyName: String): Type = {
    val t = propertyTypes.get(propertyName).orNull
    if (null == t) {
      val propertyType = PropertyUtils.getPropertyType(componentClass, propertyName)
      if (null != propertyType) return new IdentifierType(propertyType)
      else t
    } else t
  }

  def addProperty(name: String, t: Type): this.type = {
    propertyTypes += (name -> t)
    this
  }
}
