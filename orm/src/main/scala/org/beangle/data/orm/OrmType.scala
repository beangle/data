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

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.{Column, Table}
import org.beangle.data.model.meta.*

import scala.collection.mutable

trait ColumnHolder {
  def columns: Iterable[Column]
}

class SimpleColumn(column: Column) extends ColumnHolder {
  require(null != column)

  def columns: Iterable[Column] = List(column)
}

trait OrmType extends Cloneable with Type {
  def copy(): OrmType
}

trait OrmStructType extends OrmType with StructType {
  var properties: mutable.Map[String, OrmProperty] = Collections.newMap[String, OrmProperty]

  /** 获取属性对应的属性映射，支持嵌入式属性
   *
   * @param name property name
   * @return
   */
  override def property(name: String): OrmProperty = {
    val idx = name.indexOf(".")
    if (idx == -1) {
      properties(name)
    } else {
      val sp = properties(name.substring(0, idx)).asInstanceOf[OrmSingularProperty]
      sp.propertyType.asInstanceOf[OrmStructType].property(name.substring(idx + 1))
    }
  }

  override def getProperty(name: String): Option[OrmProperty] = {
    val idx = name.indexOf(".")
    if (idx == -1) {
      properties.get(name)
    } else {
      val sp = properties(name.substring(0, idx)).asInstanceOf[OrmSingularProperty]
      sp.propertyType.asInstanceOf[OrmStructType].getProperty(name.substring(idx + 1))
    }
  }

  def addProperty(property: OrmProperty): Unit = {
    properties.put(property.name, property)
  }

}

final class OrmEntityType(val entityName: String, var clazz: Class[_], var table: Table) extends OrmStructType with EntityType {
  var cacheUsage: String = _
  var cacheRegion: String = _
  var cacheAll: Boolean = _
  var isLazy: Boolean = true
  var proxy: String = _
  var isAbstract: Boolean = _
  var optimisticLockStyle: String = "NONE"
  var idGenerator: IdGenerator = _
  var module: Option[String] = None

  def cacheable: Boolean = {
    Strings.isNotBlank(cacheUsage)
  }

  def lockStyle(style: String): Unit = {
    this.optimisticLockStyle = style
  }

  def cache(region: String, usage: String): this.type = {
    this.cacheRegion = region
    this.cacheUsage = usage
    this
  }

  override def id: OrmProperty = {
    properties("id")
  }

  def copy(): this.type = {
    this
  }

  def addProperties(added: collection.Map[String, OrmProperty]): Unit = {
    if (added.nonEmpty) {
      properties ++= added
      inheritColumns(this.table, added)
    }
  }

  private def inheritColumns(table: Table, inheris: collection.Map[String, OrmProperty]): Unit = {
    inheris.values foreach {
      case spm: OrmSingularProperty =>
        spm.propertyType match {
          case etm: OrmEmbeddableType => inheritColumns(table, etm.properties)
          case _ => spm.columns foreach table.add
        }
      case _ =>
    }
  }
}

/**
 * BasicMapping
 *
 * @param clazz
 * @param column
 */
final class OrmBasicType(clazz: Class[_], var column: Column) extends BasicType(clazz)
  with OrmType with Cloneable with ColumnHolder {

  def copy(): OrmBasicType = {
    val cloned = super.clone().asInstanceOf[OrmBasicType]
    cloned.column = column.clone()
    cloned
  }

  override def columns: Iterable[Column] = {
    List(column)
  }
}

final class OrmEmbeddableType(var clazz: Class[_]) extends EmbeddableType with OrmStructType {

  var parentName: Option[String] = None

  def copy(): OrmEmbeddableType = {
    val cloned = super.clone().asInstanceOf[OrmEmbeddableType]
    val cp = Collections.newMap[String, OrmProperty]
    properties foreach {
      case (name, p) =>
        cp += (name -> p.copy())
    }
    cloned.properties = cp
    cloned
  }

}

class TypeDef(val clazz: String, val params: Map[String, String])

final class Collection(val clazz: Class[_], val property: String) {
  var cacheRegion: String = _
  var cacheUsage: String = _

  def this(clazz: Class[_], property: String, region: String, usage: String) = {
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
  val Uuid = "uuid"
  val Sequence = "sequence"
  val Identity = "identity"
  val Native = "native"
}

final class IdGenerator(val strategy: String, val autoConfig: Boolean = true) {
  val params: mutable.Map[String, String] = Collections.newMap[String, String]
  var nullValue: Option[String] = None

  def unsaved(value: String): this.type = {
    nullValue = Some(value)
    this
  }
}
