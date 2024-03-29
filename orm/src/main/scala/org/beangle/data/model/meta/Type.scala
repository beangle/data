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

package org.beangle.data.model.meta

import org.beangle.commons.lang.reflect.Reflections

trait Type {
  def clazz: Class[_]

  def newInstance(): AnyRef = {
    Reflections.newInstance(clazz).asInstanceOf[AnyRef]
  }
}

class BasicType(val clazz: Class[_]) extends Type

trait StructType extends Type {
  def getProperty(name: String): Option[Property]

  def property(name: String): Property
}

trait EmbeddableType extends StructType {
  def parentName: Option[String]
}

trait EntityType extends StructType {
  def id: Property

  def entityName: String

  def partitionKey: Option[String]

  def cacheable: Boolean
}

trait Property {
  def name: String

  def clazz: Class[_]

  def optional: Boolean
}

trait SingularProperty extends Property {
  def propertyType: Type
}

trait PluralProperty extends Property {
  def element: Type
}

trait CollectionProperty extends PluralProperty {
  def orderBy: Option[String]
}

trait MapProperty extends PluralProperty {
  def key: Type
}
