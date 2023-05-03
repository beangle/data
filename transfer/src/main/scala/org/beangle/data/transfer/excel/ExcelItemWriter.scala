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

package org.beangle.data.transfer.excel

import org.apache.poi.ss.usermodel.{CellType, FillPatternType, HorizontalAlignment, VerticalAlignment}
import org.apache.poi.xssf.streaming.{SXSSFSheet, SXSSFWorkbook}
import org.apache.poi.xssf.usermodel.*
import org.beangle.commons.lang.{Chars, Numbers}
import org.beangle.data.excel.CellOps.*
import org.beangle.data.excel.ExcelStyleRegistry
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.exporter.ExportContext
import org.beangle.data.transfer.io.ItemWriter

import java.io.OutputStream

/**
 * ExcelItemWriter class.
 *
 * @author chaostone
 */
class ExcelItemWriter(val context: ExportContext, val outputStream: OutputStream) extends ItemWriter {

  private var workbook: SXSSFWorkbook = _ // 建立新XSSFWorkbook对象

  private var sheet: SXSSFSheet = _

  private implicit var registry: ExcelStyleRegistry = _

  var title: Any = _

  var flushCount = 1000

  var countPerSheet = 100000

  private var index = 0

  init()

  def init(): Unit = {
    if (null != context) {
      val count = context.datas.getOrElse("countPerSheet", "")
      if (null != count && Numbers.isDigits(count.toString)) {
        val countParam = Numbers.toInt(count.toString)
        if (countParam > 0) this.countPerSheet = countParam
      }
    }
    workbook = new SXSSFWorkbook(flushCount)
    registry = new ExcelStyleRegistry(workbook)
  }

  def close(): Unit = {
    try {
      workbook.write(outputStream)
    } finally {}
    workbook.dispose()
  }

  override def write(obj: Any): Unit = {
    if (index + 1 >= countPerSheet) {
      writeTitle(null, title)
    }
    writeItem(obj)
    index += 1
  }

  override def writeTitle(titleName: String, data: Any): Unit = {
    if (null != titleName) {
      sheet = workbook.createSheet(titleName)
    } else {
      sheet = workbook.createSheet()
    }
    title = data
    index = 0
    writeItem(data)
    val titleRow = sheet.getRow(index)
    val titleStyle = buildTitleStyle()
    val titles = data.asInstanceOf[Array[String]]
    for (i <- 0 until titleRow.getLastCellNum()) {
      titleRow.getCell(i).setCellStyle(titleStyle)
      sheet.setColumnWidth(i, 256 * (2 + Chars.charLength(titles(i)))) // 2 is margin
    }
    index += 1
    sheet.createFreezePane(0, 1)
  }

  def format: Format = {
    Format.Xlsx
  }

  protected def writeItem(datas: Any): Unit = {
    val row = sheet.createRow(index) // 建立新行
    if (datas != null) {
      if (datas.getClass.isArray) {
        val values = datas.asInstanceOf[Array[_]]
        values.indices foreach { i =>
          row.createCell(i).fillin(values(i))
        }
      } else {
        val cell = row.createCell(0)
        datas match {
          case _: Number => cell.setCellType(CellType.NUMERIC)
          case _ =>
        }
        cell.setCellValue(new XSSFRichTextString(datas.toString))
      }
    }
  }

  protected def buildTitleStyle(): XSSFCellStyle = {
    val style = workbook.createCellStyle().asInstanceOf[XSSFCellStyle]
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    val rgb = Array(221.toByte, 217.toByte, 196.toByte)
    style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap))
    style
  }
}
