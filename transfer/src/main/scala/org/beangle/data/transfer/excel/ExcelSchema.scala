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
package org.beangle.data.transfer.excel

import java.io.File

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.{Numbers, Strings}

import scala.collection.mutable

class ExcelSchema {

  def createScheet(name: String = ""): ExcelScheet = {
    val sheetName =
      if (Strings.isBlank(name)) {
        "Sheet" + (sheets.size + 1)
      } else {
        name
      }
    val sheet = new ExcelScheet(sheetName)
    sheets += sheet
    sheet
  }

  def generate(file: File): Unit = {
    ExcelSchemaWriter.generate(this, file)
  }

  val sheets: mutable.Buffer[ExcelScheet] = Collections.newBuffer[ExcelScheet]
}

class ExcelScheet(var name: String) {

  var title: Option[String] = None
  var remark: Option[String] = None

  val columns: mutable.Buffer[ExcelColumn] = Collections.newBuffer[ExcelColumn]

  def title(t: String): this.type = {
    this.title = Some(t)
    this
  }

  def remark(r: String): this.type = {
    this.remark = Some(r)
    this
  }

  def add(name: String): ExcelColumn = {
    val column = new ExcelColumn(name)
    columns += column
    column
  }

  def add(name: String, comment: String): ExcelColumn = {
    val column = new ExcelColumn(name)
    columns += column
    column.comment = Some(comment)
    column
  }
}

class ExcelColumn(var name: String) {
  var comment: Option[String] = None
  var remark: Option[String] = None
  /** 文本长度 */
  var length: Option[Int] = None
  /** 日期格式 */
  var date: Option[String] = None

  var isInt: Boolean = _
  var isDecimal: Boolean = _
  var refs: collection.Seq[String] = _
  var datas: collection.Seq[String] = _
  var formular1: String = _
  var formular2: Option[String] = None
  var required: Boolean = _
  var unique: Boolean = _

  def remark(r: String): this.type = {
    this.remark = Some(r)
    this
  }

  def unique(nv: Boolean = true): this.type = {
    this.unique = nv
    this
  }

  def required(r: Boolean = true): this.type = {
    this.required = r
    this
  }

  def date(format: String = "yyyy-MM-dd"): this.type = {
    date = Some(format)
    formular1 = format
    formular1 = Strings.replace(formular1, "yyyy", "1900")
    formular1 = Strings.replace(formular1, "yy", "00")
    formular1 = Strings.replace(formular1, "MM", "01")
    formular1 = Strings.replace(formular1, "dd", "01")
    this
  }

  def min(formular: Any): this.type = {
    this.formular1 = formular.toString
    this
  }

  def max(formular: Any): this.type = {
    this.length = Some(Numbers.toInt(formular.toString))
    if (null == this.formular1) {
      this.formular1 = "0"
    }
    this.formular2 = Some(formular.toString)
    this
  }

  def length(max: Int): this.type = {
    length = Some(max)
    if (null == formular1) {
      formular1 = "0"
    }
    formular2 = Some(max.toString)
    this
  }

  def decimal(): this.type = {
    isDecimal = true
    formular1 = "0"
    this
  }

  def decimal(min: Float, max: Float): this.type = {
    isDecimal = true
    assert(max >= min)
    formular1 = min.toString
    formular2 = Some(max.toString)
    this
  }

  def integer(): this.type = {
    isInt = true
    formular1 = "0"
    this
  }

  def integer(min: Int, max: Int): this.type = {
    isInt = true
    assert(max >= min)
    formular1 = min.toString
    formular2 = Some(max.toString)
    this
  }

  def ref(data: collection.Seq[String]): this.type = {
    this.refs = data
    this
  }

  def data(data: collection.Seq[String]): this.type = {
    this.datas = data
    this
  }
}