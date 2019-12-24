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

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.{Column, Table}
import org.beangle.data.model.meta._

import scala.collection.mutable

trait ColumnHolder {
  def columns: Iterable[Column]
}

class SimpleColumn(column: Column) extends ColumnHolder {
  require(null != column)
  val columns: mutable.Buffer[Column] = mutable.Buffer.empty[Column]
  columns += column
}

trait TypeMapping extends Cloneable {
  def typ: Type

  def copy(): TypeMapping
}

trait StructTypeMapping extends TypeMapping {
  var properties: mutable.Map[String, PropertyMapping[_]] = Collections.newMap[String, PropertyMapping[_]]

  /** 获取属性对应的属性映射，支持嵌入式属性
    *
    * @param property
    * @return
    */
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
  var cacheAll: Boolean = _
  var isLazy: Boolean = true
  var proxy: String = _
  var isAbstract: Boolean = _
  var idGenerator: IdGenerator = _

  def cacheable: Boolean = {
    Strings.isNotBlank(cacheUsage)
  }

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

  def addProperties(added: collection.Map[String, PropertyMapping[_]]): Unit = {
    if (added.nonEmpty) {
      properties ++= added
      inheriteColumns(this.table, added)
    }
  }

  private def inheriteColumns(table: Table, inheris: collection.Map[String, PropertyMapping[_]]): Unit = {
    inheris.values foreach {
      case spm: SingularPropertyMapping =>
        spm.mapping match {
          case btm: BasicTypeMapping => btm.columns foreach (table.add(_))
          case etm: EmbeddableTypeMapping => inheriteColumns(table, etm.properties)
          case _ =>
        }
      case _ =>
    }
  }
}

final class BasicTypeMapping(val typ: BasicType, column: Column)
  extends TypeMapping with Cloneable with ColumnHolder {

  var columns: mutable.Buffer[Column] = mutable.Buffer.empty[Column]

  if (null != column) columns += column

  def copy(): BasicTypeMapping = {
    val cloned = super.clone().asInstanceOf[BasicTypeMapping]
    val cc = mutable.Buffer.empty[Column]
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

  def this(clazz: Class[_], property: String, region: String, usage: String) {
    this(clazz, property)
    cache(region, usage)
  }

  def cache(region: String, usage: String): this.type = {
    this.cacheRegion = region
    this.cacheUsage = usage
    this
  }
}

object IdGenerator {
  val Date = "date"
  val DateTime = "datetime"
  val AutoIncrement = "auto_increment"
  val SeqPerTable = "seq_per_table"
  val Code = "code"
  val Assigned = "assigned"
}

final class IdGenerator(var name: String) {
  val params: mutable.Map[String, String] = Collections.newMap[String, String]
  var nullValue: Option[String] = None

  def unsaved(value: String): this.type = {
    nullValue = Some(value)
    this
  }
}
