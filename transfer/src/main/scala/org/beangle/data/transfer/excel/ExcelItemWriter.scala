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

  protected var workbook: SXSSFWorkbook = _ // 建立新XSSFWorkbook对象

  protected var sheet: SXSSFSheet = _

  private implicit var registry: ExcelStyleRegistry = _

  protected var title: Any = _

  var flushCount = 1000

  var countPerSheet = 100000

  protected var index = 0

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

  def createScheet(name: String): SXSSFSheet = {
    if (null == sheet || null != name && !(this.sheet.getSheetName == name)) {
      this.sheet = if null != name then this.workbook.createSheet(name) else this.workbook.createSheet()
    }
    this.sheet
  }

  override def writeTitle(sheetName: String, data: Any): Unit = {
    writeTitle(data)
  }

  override def writeTitle(data: Any): Unit = {
    createScheet(null)
    title = data
    index = 0
    writeItem(data)
    val titleRow = sheet.getRow(index)
    val titleStyle = buildTitleStyle()
    val titles = data match
      case cs: Array[String] => cs
      case it: Iterable[Any] => it.toArray.map(_.toString)
      case _ => throw new RuntimeException("cannot write title with " + String.valueOf(data))

    val maxWith = 15 * 2 //max 15 chinese chars
    var h = 0d // number rows
    for (i <- titles.indices) {
      titleRow.getCell(i).setCellStyle(titleStyle)
      val n = Chars.charLength(titles(i))
      val w = Math.min(n, maxWith)
      val r = n * 1.0 / maxWith
      if (r > h) h = r
      sheet.setColumnWidth(i.toShort, (w + 4) * 256) // 4 is margin
    }
    var height = Math.ceil(h).toInt
    if (height > 8) height = 8
    titleRow.setHeight((height * 12 * 20).toShort)

    index += 1
    sheet.createFreezePane(0, index)
  }

  def format: Format = {
    Format.Xlsx
  }

  protected def writeItem(datas: Any): Unit = {
    val row = sheet.createRow(index) // 建立新行
    datas match
      case null =>
      case a: Array[Any] =>
        a.indices foreach { i => row.createCell(i).fillin(a(i)) }
      case it: Iterable[Any] =>
        var i = 0
        it.foreach { obj => row.createCell(i).fillin(obj); i += 1 }
      case n: Number =>
        val cell = row.createCell(0)
        cell.setCellType(CellType.NUMERIC)
        cell.setCellValue(new XSSFRichTextString(n.toString))
      case a: Any =>
        val cell = row.createCell(0)
        cell.setCellValue(new XSSFRichTextString(a.toString))
  }

  protected def buildTitleStyle(): XSSFCellStyle = {
    val style = workbook.createCellStyle().asInstanceOf[XSSFCellStyle]
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.setWrapText(true) //auto wrap text

    val rgb = Array(221.toByte, 217.toByte, 196.toByte)
    style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap))
    style
  }
}
