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

import scala.collection.mutable.Buffer

import org.beangle.commons.collection.Collections
import org.beangle.data.jdbc.meta.{ Column, Table }
import org.beangle.data.model.meta.{ BasicType, EmbeddableType, EntityType, Property, Type }

trait ColumnHolder {
  def columns: Iterable[Column]
}

class SimpleColumn(column: Column) extends ColumnHolder {
  require(null != column)
  val columns: Buffer[Column] = Buffer.empty[Column]
  columns += column
}

trait TypeMapping extends Cloneable {
  def typ: Type
  def copy(): TypeMapping
}

trait StructTypeMapping extends TypeMapping {
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
    extends TypeMapping with Cloneable with ColumnHolder {

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

object IdGenerator {
  val Date = "date"
  val AutoIncrement = "auto_increment"
  val SeqPerTable = "seq_per_table"
  val Code = "code"
  val Assigned="assigned"
}

final class IdGenerator(var name: String) {
  val params = Collections.newMap[String, String]
  var nullValue: Option[String] = None

  def unsaved(value: String): this.type = {
    nullValue = Some(value)
    this
  }
}

