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

package org.beangle.data.transfer.exporter

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.DataType
import org.beangle.commons.lang.text.{Formatter, Formatters}
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.io.Writer

class ExportContext {

  var exporter: Exporter = _

  var writer: Writer = _

  /** Convert all property to string before export */
  var convertToString: Boolean = false

  var typeFormatters: Map[Class[_], Formatter] = Map.empty

  var propertyFormatters: Map[String, Formatter] = Map.empty

  def registerFormatter(clazz: Class[_], formatter: Formatter): ExportContext = {
    typeFormatters += (clazz -> formatter)
    this
  }

  def registerPattern(propertyName: String, formatter: Formatter): ExportContext = {
    propertyFormatters += (propertyName -> formatter)
    this
  }

  def getFormatter(propertyName: String, obj: Any): Option[Formatter] = {
    propertyFormatters.get(propertyName) match {
      case None => if (null == obj) None else typeFormatters.get(obj.getClass)
      case p@Some(f) => p
    }
  }

  val datas: collection.mutable.Map[String, Any] = Collections.newMap[String, Any]

  var format: Format = _

  var extractor: PropertyExtractor = new DefaultPropertyExtractor

  def get[T](key: String, clazz: Class[T]): Option[T] = {
    datas.get(key).asInstanceOf[Option[T]]
  }

  def put(key: String, v: Any): Unit = {
    datas.put(key, v)
  }

  def getPropertyValue(target: Object, property: String): Any = {
    val value = extractor.getPropertyValue(target, property)
    getFormatter(property, value) match {
      case None =>
        if convertToString then
          if (value == null) then "" else Formatters.getDefault(value.getClass).format(value)
        else
          value
      case Some(formatter) => formatter.format(value)
    }
  }
}
