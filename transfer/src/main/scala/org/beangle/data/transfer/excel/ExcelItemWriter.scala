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

import java.io.OutputStream
import java.time._
import java.time.temporal.Temporal

import org.apache.poi.ss.usermodel.{CellType, FillPatternType, HorizontalAlignment, VerticalAlignment}
import org.apache.poi.xssf.usermodel._
import org.beangle.commons.lang.Numbers
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.excel.CellOps._
import org.beangle.data.transfer.exporter.ExportContext
import org.beangle.data.transfer.io.ItemWriter

/**
  * ExcelItemWriter class.
  *
  * @author chaostone
  * @version $Id: $
  */
class ExcelItemWriter(val context: ExportContext, val outputStream: OutputStream) extends ItemWriter {

  private var workbook: XSSFWorkbook = _ // 建立新XSSFWorkbook对象

  private var sheet: XSSFSheet = _

  private implicit var registry: ExcelStyleRegistry = _

  var title: Any = _

  var flushCount = 1000

  var countPerSheet = 100000

  var index = 0

  init()


  def init(): Unit = {
    workbook = new XSSFWorkbook()
    registry = new ExcelStyleRegistry(workbook)

    if (null != context) {
      val count = context.datas.getOrElse("countPerSheet", "")
      if (null != count && Numbers.isDigits(count.toString)) {
        val countParam = Numbers.toInt(count.toString)
        if (countParam > 0) this.countPerSheet = countParam
      }
    }
  }


  def fillin(cell: XSSFCell, value: Any): Unit = {
    val v =
      value match {
        case o: Option[_] => o.orNull
        case _ => value
      }

    if (null == v) {
      cell.setCellValue("")
    } else {
      v match {
        case d: java.util.Date =>
          d match {
            case sd: java.sql.Date => cell.fill(sd)
            case st: java.sql.Timestamp => cell.fill(st)
            case stt: java.sql.Time => cell.fill(stt)
            case _ => cell.fill(d)
          }
        case uc: java.util.Calendar => cell.fill(uc.getTime)
        case t: Temporal =>
          t match {
            case ld: LocalDate => cell.fill(java.sql.Date.valueOf(ld))
            case i: Instant => cell.fill(java.util.Date.from(i))
            case ldt: LocalDateTime => cell.fill(java.util.Date.from(ldt.atZone(ZoneId.systemDefault).toInstant))
            case zdt: ZonedDateTime => cell.fill(java.util.Date.from(zdt.toInstant))
            case lt: LocalTime => cell.fill(java.sql.Time.valueOf(lt))
            case y: Year => cell.fill(y.getValue)
            case yt: YearMonth => cell.fill(yt)
          }
        case n: Number =>
          n match {
            case i: Integer => cell.fill(i.intValue())
            case f: java.lang.Float => cell.fill(f.floatValue())
            case d: java.lang.Double => cell.fill(d.doubleValue())
            case _ => cell.fill(n.intValue())
          }
        case b: java.lang.Boolean => cell.fill(b.booleanValue())
        case _ => cell.fill(v.toString)
      }
    }
  }

  def close(): Unit = {
    workbook.write(outputStream)
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
    for (i <- 0 until titleRow.getLastCellNum()) {
      titleRow.getCell(i).setCellStyle(titleStyle)
    }
    index += 1
    sheet.createFreezePane(0, 1)
  }

  def format: Format.Value = {
    Format.Xlsx
  }

  protected def writeItem(datas: Any): Unit = {
    val row = sheet.createRow(index) // 建立新行
    if (datas != null) {
      if (datas.getClass.isArray) {
        val values = datas.asInstanceOf[Array[_]]
        values.indices foreach { i =>
          fillin(row.createCell(i), values(i))
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
    val style = workbook.createCellStyle()
    style.setAlignment(HorizontalAlignment.CENTER) // 左右居中
    style.setVerticalAlignment(VerticalAlignment.CENTER) // 上下居中
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    val rgb = Array(221.toByte, 217.toByte, 196.toByte)
    style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap))
    style
  }
}
