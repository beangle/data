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

import org.beangle.data.model.Entity
import org.beangle.commons.lang.Assert
import org.beangle.commons.bean.PropertyUtils

object EntityType {
  def extractIdType(entityClass: Class[_], idName: String): Type = {
    val clazz = PropertyUtils.getPropertyType(entityClass, idName)
    if (null != clazz) new IdentifierType(clazz) else null
  }
}
/**
 * EntityType class.
 *
 * @author chaostone
 */
class EntityType(val entityClass: Class[_], val entityName: String, val idName: String = "id", val propertyTypes: Map[String, Type]) extends AbstractType {
  assert(null != idName && null != entityName && null != entityClass)

  override def isEntityType = true

  /**
   * Get the type of a particular (named) property
   */
  override def getPropertyType(property: String): Option[Type] = propertyTypes.get(property)

  override def name: String = entityName

  override def returnedClass = entityClass
}
