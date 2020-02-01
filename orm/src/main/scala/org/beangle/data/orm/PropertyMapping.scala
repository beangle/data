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
package org.beangle.data.orm

import org.beangle.data.jdbc.meta.Column
import org.beangle.data.model.meta._

trait Fetchable {
  var fetch: Option[String] = None
}

abstract class PropertyMapping[T <: Property](val property: T) {
  var cascade: Option[String] = None
  var mergeable: Boolean = true

  var updateable: Boolean = true
  var insertable: Boolean = true
  var optimisticLocked: Boolean = true
  var lazyed: Boolean = false
  var generator: IdGenerator = _
  var generated: Option[String] = None

  def copy(): this.type
}

final class SingularPropertyMapping(property: SingularProperty, var mapping: TypeMapping)
  extends PropertyMapping(property) with Fetchable with ColumnHolder with Cloneable {
  def copy: this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.mapping = this.mapping.copy()
    cloned
  }

  def columns: Iterable[Column] = {
    mapping match {
      case s: BasicTypeMapping => s.columns
      case _ => throw new RuntimeException("Columns on apply on BasicTypeMapping")
    }
  }
}

abstract class PluralPropertyMapping[T <: PluralProperty](property: T, var element: TypeMapping)
  extends PropertyMapping(property) with Fetchable with Cloneable {
  var ownerColumn: Column = _
  var mappedBy: Option[String] = None
  var inverse: Boolean = false
  var where: Option[String] = None
  var batchSize: Option[Int] = None
  var index: Option[Column] = None
  var table: Option[String] = None
  var subselect: Option[String] = None
  var sort: Option[String] = None

  var one2many = false

  def many2many: Boolean = !one2many

  def copy: this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.element = this.element.copy()
    cloned
  }
}

class CollectionPropertyMapping(property: CollectionProperty, element: TypeMapping) extends PluralPropertyMapping(property, element)

final class MapPropertyMapping(property: MapProperty, var key: TypeMapping, element: TypeMapping)
  extends PluralPropertyMapping(property, element) {

  override def copy(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.element = this.element.copy()
    cloned.key = this.key.copy()
    cloned
  }
}
