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
import org.beangle.commons.lang.{Options, Strings}
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.csv.CsvItemWriter
import org.beangle.data.transfer.excel.{ExcelItemWriter, ExcelTemplateExporter, ExcelTemplateWriter}
import org.beangle.data.transfer.io.Writer

import java.io.OutputStream
import java.net.URL
import scala.collection.mutable

class ExportContext {

  var exporter: Exporter = _

  var writer: Writer = _

  val datas: collection.mutable.Map[String, Any] = Collections.newMap[String, Any]

  var format: Format = _

  var extractor: PropertyExtractor = new DefaultPropertyExtractor
  /** Convert all property to string before export */
  var convertToString: Boolean = false

  var typeFormatters: Map[Class[_], Formatter] = Map.empty

  var propertyFormatters: Map[String, Formatter] = Map.empty

  val sharedValues = Collections.newMap[String, String]

  var fileName: String = _
  var titles: Array[String] = _
  var attrs: Array[String] = _

  def registerFormatter(clazz: Class[_], formatter: Formatter): ExportContext = {
    typeFormatters += (clazz -> formatter)
    this
  }

  def registerFormatter(propertyName: String, formatter: Formatter): ExportContext = {
    propertyFormatters += (propertyName -> formatter)
    this
  }

  def getFormatter(propertyName: String, obj: Any): Option[Formatter] = {
    propertyFormatters.get(propertyName) match {
      case None => if (null == obj) None else typeFormatters.get(obj.getClass)
      case p@Some(f) => p
    }
  }

  def writeTo(os: OutputStream, format: Format, suggestFileName: Option[String]): ExportContext = {
    this.format = format
    setFileName(suggestFileName)
    this.writer =
      if format == Format.Xlsx then new ExcelItemWriter(this, os)
      else if format == Format.Csv then new CsvItemWriter(this, os)
      else throw new RuntimeException("Cannot export to other formats, csv/xlsx supported only!")

    if (this.format == Format.Csv) this.convertToString = true
    this.exporter = new SimpleEntityExporter()
    this
  }

  def writeTo(os: OutputStream, format: Format, suggestFileName: Option[String], template: URL): ExportContext = {
    if format != Format.Xlsx then throw new RuntimeException("Xlsx supported only!")
    this.format = Format.Xlsx
    setFileName(suggestFileName)
    this.writer = new ExcelTemplateWriter(template, this, os)
    this.exporter = new ExcelTemplateExporter()
    this.convertToString = false
    this
  }

  private def setFileName(suggest: Option[String]): String = {
    val ext = "." + Strings.uncapitalize(this.format.toString)
    this.fileName = suggest match {
      case Some(f) => if (!f.endsWith(ext)) f + ext else f
      case None => "exportFile" + ext
    }
    this.fileName
  }

  def setTitles(properties: String, convertToString: Option[Boolean]): Unit = {
    val props = Strings.split(properties, ",")
    val keys = new mutable.ArrayBuffer[String](props.length)
    val titles = new mutable.ArrayBuffer[String](props.length)
    for (prop <- props) {
      if (prop.contains(":")) {
        var key = Strings.substringBefore(prop, ":")
        var sharedValue = ""
        if (key.contains("(")) {
          sharedValue = Strings.substringBetween(key, "(", ")")
          key = Strings.substringBefore(key, "(")
          sharedValues.put(key, sharedValue)
        } else if (key.startsWith("blank.")) {
          sharedValues.put(key, sharedValue)
        }
        keys += key
        titles += Strings.substringAfter(prop, ":")
      } else {
        titles += prop
      }
    }
    if keys.nonEmpty then this.attrs = keys.toArray
    this.titles = titles.toArray
    convertToString foreach { x => this.convertToString = x }
  }

  def get[T](key: String, clazz: Class[T]): Option[T] = {
    datas.get(key).asInstanceOf[Option[T]]
  }

  def put(key: String, v: Any): Unit = {
    datas.put(key, v)
  }

  def getPropertyValue(target: Object, property: String): Any = {
    sharedValues.get(property) match
      case Some(v) => v
      case None =>
        val value = Options.unwrap(extractor.getPropertyValue(target, property))
        if value == null then ""
        else
          getFormatter(property, value) match {
            case None => if convertToString then Formatters.getDefault(value.getClass).format(value) else value
            case Some(formatter) => formatter.format(value)
          }
  }
}
