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

package org.beangle.data.orm

import org.beangle.data.jdbc.meta.Column
import org.beangle.data.model.meta._

trait Fetchable {
  var fetch: Option[String] = None
}

abstract class OrmProperty(val name: String, val clazz: Class[_], var optional: Boolean) extends Property {
  var cascade: Option[String] = None
  var mergeable: Boolean = true

  var updatable: Boolean = true
  var insertable: Boolean = true
  var optimisticLocked: Boolean = true
  var isLazy: Boolean = false
  var generator: IdGenerator = _
  var generated: Option[String] = None

  def copy(): OrmProperty
}

final class OrmSingularProperty(name: String, clazz: Class[_], optional: Boolean, var propertyType: OrmType)
  extends OrmProperty(name, clazz, optional) with Fetchable with ColumnHolder with Cloneable with SingularProperty {

  var joinColumn: Option[Column] = None

  def copy(): OrmSingularProperty = {
    val cloned = super.clone().asInstanceOf[OrmSingularProperty]
    cloned.propertyType = this.propertyType.copy()
    cloned.joinColumn = joinColumn.map(_.clone())
    cloned
  }

  def columns: Iterable[Column] = {
    propertyType match {
      case b: OrmBasicType => List(b.column)
      case _: OrmEntityType => joinColumn
      case _ => throw new RuntimeException("Cannot support iterable column over Embedded")
    }
  }

}

abstract class OrmPluralProperty(name: String, clazz: Class[_], var element: OrmType)
  extends OrmProperty(name, clazz, true) with PluralProperty with Fetchable with Cloneable {
  var ownerColumn: Column = _
  var inverseColumn: Option[Column] = None
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

  def copy(): OrmPluralProperty = {
    val cloned = super.clone().asInstanceOf[OrmPluralProperty]
    cloned.element = this.element.copy()
    cloned
  }
}

class OrmCollectionProperty(name: String, clazz: Class[_], element: OrmType)
  extends OrmPluralProperty(name, clazz, element) with CollectionProperty {
  var orderBy: Option[String] = None
}

final class OrmMapProperty(name: String, clazz: Class[_], var key: OrmType, elem: OrmType)
  extends OrmPluralProperty(name, clazz, elem) with MapProperty {

  var keyColumn: Column = _

  override def copy(): OrmMapProperty = {
    val cloned = super.clone().asInstanceOf[OrmMapProperty]
    cloned.element = this.element.copy()
    cloned
  }
}
