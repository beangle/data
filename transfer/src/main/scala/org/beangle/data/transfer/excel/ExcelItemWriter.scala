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

import java.time.{ Instant, LocalDate }
import org.apache.poi.hssf.usermodel.{ HSSFCellStyle, HSSFRichTextString, HSSFSheet, HSSFWorkbook }
import org.apache.poi.hssf.util.HSSFColor.HSSFColorPredefined
import org.apache.poi.ss.usermodel.{ CellType, FillPatternType, HorizontalAlignment, VerticalAlignment }
import org.beangle.commons.lang.Numbers
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.io.ItemWriter
import org.beangle.data.transfer.exporter.Context

/**
 * ExcelItemWriter class.
 *
 * @author chaostone
 * @version $Id: $
 */
class ExcelItemWriter(val context: Context) extends ItemWriter {

  var countPerSheet = 50000

  var workbook = new HSSFWorkbook() // 建立新HSSFWorkbook对象

  var index = 0

  var sheet: HSSFSheet = _

  var dateStyle: HSSFCellStyle = _

  var timeStyle: HSSFCellStyle = _

  var title: Any = _

  init()

  def init() {
    if (null != context) {
      val count = context.datas.get("countPerSheet").getOrElse("")
      if (null != count && Numbers.isDigits(count.toString())) {
        val countParam = Numbers.toInt(count.toString)
        if (countParam > 0) this.countPerSheet = countParam
      }
    }
  }

  def close() {
    workbook.write(outputStream)
  }

  override def write(obj: Any) {
    if (index + 1 >= countPerSheet) {
      writeTitle(null, title)
    }
    writeItem(obj)
    index += 1
  }

  override def writeTitle(titleName: String, data: Any) {
    if (null != titleName) {
      sheet = workbook.createSheet(titleName)
    } else {
      sheet = workbook.createSheet()
    }
    title = data
    index = 0
    writeItem(data)
    var titleRow = sheet.getRow(index)
    var titleStyle = buildTitleStyle()
    for (i <- 0 until titleRow.getLastCellNum()) {
      titleRow.getCell(i).setCellStyle(titleStyle)
    }
    index += 1
  }

  def format: Format.Value = {
    Format.Xls
  }

  protected def writeItem(datas: Any) {
    var row = sheet.createRow(index) // 建立新行
    if (datas != null) {
      if (datas.getClass().isArray()) {
        val values = datas.asInstanceOf[Array[_]]
        for (i <- 0 until values.length) {
          val cell = row.createCell(i)
          var v = values(i)
          if (null != v && v.isInstanceOf[Option[_]]) {
            v = v.asInstanceOf[Option[_]].getOrElse(null)
          }
          v match {
            case n: Number =>
              cell.setCellType(CellType.NUMERIC)
              cell.setCellValue(n.doubleValue())
            case d: java.sql.Date =>
              cell.setCellValue(d)
              cell.setCellStyle(getDateStyle())
            case t: java.util.Date =>
              cell.setCellValue(t)
              cell.setCellStyle(getTimeStyle())
            case t: LocalDate =>
              cell.setCellValue(java.sql.Date.valueOf(t))
              cell.setCellStyle(getDateStyle())
            case t: Instant =>
              cell.setCellValue(java.util.Date.from(t))
              cell.setCellStyle(getTimeStyle())
            case c: java.util.Calendar =>
              cell.setCellValue(c)
              cell.setCellStyle(getTimeStyle())
            case _ =>
              cell.setCellValue(new HSSFRichTextString(if (v == null) "" else v.toString))
          }
        }
      } else {
        var cell = row.createCell(0)
        datas match {
          case n: Number => cell.setCellType(CellType.NUMERIC)
          case _         =>
        }
        cell.setCellValue(new HSSFRichTextString(datas.toString()))
      }
    }
  }

  private def getDateStyle(): HSSFCellStyle = {
    if (null == dateStyle) {
      dateStyle = workbook.createCellStyle()
      dateStyle.setDataFormat(workbook.createDataFormat().getFormat(getDateFormat()))
    }
    dateStyle
  }

  private def getTimeStyle(): HSSFCellStyle = {
    if (null == timeStyle) {
      timeStyle = workbook.createCellStyle()
      timeStyle.setDataFormat(workbook.createDataFormat().getFormat(getDateTimeFormat()))
    }
    timeStyle
  }

  protected def getDateFormat(): String = {
    return "YYYY-MM-DD"
  }

  protected def getDateTimeFormat(): String = {
    return "YYYY-MM-DD HH:MM:SS"
  }

  protected def buildTitleStyle(): HSSFCellStyle = {
    val style = workbook.createCellStyle()
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.setFillForegroundColor(HSSFColorPredefined.GREY_25_PERCENT.getIndex())
    style
  }
}
