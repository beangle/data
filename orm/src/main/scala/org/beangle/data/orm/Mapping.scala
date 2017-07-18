/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.orm

import org.beangle.data.model.meta._
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.jdbc.meta.Column
import org.beangle.data.jdbc.meta.SqlType
import org.beangle.commons.collection.Collections
import scala.collection.mutable.Buffer
import java.sql.Types

class SimpleColumn(column: Column) extends ColumnHolder {
  require(null != column)
  val columns: Buffer[Column] = Buffer.empty[Column]
  columns += column
}

trait Fetchable {
  var fetch: Option[String] = None
}
trait ColumnHolder {
  def columns: Iterable[Column]
}

trait Mapping extends Cloneable {
  def typ: Type
  def copy(): Mapping
}

trait StructTypeMapping extends Mapping {
  var properties = Collections.newMap[String, PropertyMapping[_]]
  def getPropertyMapping(property: String): PropertyMapping[_] = {
    val idx = property.indexOf(".")
    if (idx == -1) {
      properties(property)
    } else {
      val sp = properties(property.substring(0, idx)).asInstanceOf[SingularPropertyMapping]
      sp.mapping.asInstanceOf[StructTypeMapping].getPropertyMapping(property.substring(idx + 1))
    }
  }
}

final class EntityTypeMapping(var typ: EntityType, var table: Table) extends StructTypeMapping {
  var cacheUsage: String = _
  var cacheRegion: String = _
  var isLazy: Boolean = true
  var proxy: String = _
  var isAbstract: Boolean = _
  var idGenerator: IdGenerator = _

  def cache(region: String, usage: String): this.type = {
    this.cacheRegion = region
    this.cacheUsage = usage
    this
  }

  def clazz: Class[_] = {
    typ.clazz
  }

  def entityName: String = {
    typ.entityName
  }
  def copy(): this.type = {
    this
  }
}

final class BasicTypeMapping(val typ: BasicType, column: Column)
    extends Mapping with Cloneable with ColumnHolder {

  var columns: Buffer[Column] = Buffer.empty[Column]

  if (null != column) columns += column

  def copy(): BasicTypeMapping = {
    val cloned = super.clone().asInstanceOf[BasicTypeMapping]
    val cc = Buffer.empty[Column]
    columns foreach { c =>
      cc += c.clone()
    }
    cloned.columns = cc
    cloned
  }

}

final class EmbeddableTypeMapping(val typ: EmbeddableType) extends StructTypeMapping {

  def copy(): EmbeddableTypeMapping = {
    val cloned = super.clone().asInstanceOf[EmbeddableTypeMapping]
    val cp = Collections.newMap[String, PropertyMapping[_]]
    properties foreach {
      case (name, p) =>
        cp += (name -> p.copy().asInstanceOf[PropertyMapping[Property]])
    }
    cloned.properties = cp
    cloned
  }

}

abstract class PropertyMapping[T <: Property](val property: T) {
  var access: Option[String] = None
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

final class SingularPropertyMapping(property: SingularProperty, var mapping: Mapping)
    extends PropertyMapping(property) with Fetchable with ColumnHolder with Cloneable {
  def copy: this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.mapping = this.mapping.copy()
    cloned
  }

  def columns: Iterable[Column] = {
    mapping match {
      case s: BasicTypeMapping => s.columns
      case _                   => throw new RuntimeException("Columns on apply on BasicTypeMapping")
    }
  }
}

abstract class PluralPropertyMapping[T <: PluralProperty](property: T, var element: Mapping)
    extends PropertyMapping(property) with Fetchable with Cloneable {
  var ownerColumn: Column = _
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

class CollectionMapping(property: CollectionProperty, element: Mapping) extends PluralPropertyMapping(property, element)

final class MapMapping(property: MapProperty, var key: Mapping, element: Mapping)
    extends PluralPropertyMapping(property, element) {

  override def copy(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.element = this.element.copy()
    cloned.key = this.key.copy()
    cloned
  }
}

class TypeDef(val clazz: String, val params: Map[String, String])

final class Collection(val clazz: Class[_], val property: String) {
  var cacheRegion: String = _
  var cacheUsage: String = _

  def cache(region: String, usage: String): this.type = {
    this.cacheRegion = region
    this.cacheUsage = usage
    this
  }
}

final class IdGenerator(var name: String) {
  val params = Collections.newMap[String, String]
  var nullValue: Option[String] = None

  def unsaved(value: String): this.type = {
    nullValue = Some(value)
    this
  }
}
