package org.beangle.data.transfer.excel

import java.time.{MonthDay, Year, YearMonth}

import org.apache.poi.ss.usermodel.{Cell, CellType}
import org.apache.poi.xssf.usermodel.XSSFRichTextString

object CellOps {
  @inline implicit def toCell(x: Cell): CellOps = new CellOps(x)
}

final class CellOps(private val cell: Cell) extends AnyVal {

  def fill(d: java.sql.Date)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.Date))
    cell.setCellType(CellType.NUMERIC)
  }

  def fill(d: java.util.Date)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellStyle(registry.get(DataType.DateTime))
    cell.setCellType(CellType.NUMERIC)
  }

  def fill(d: YearMonth)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(java.sql.Date.valueOf(d.atDay(1)))
    cell.setCellStyle(registry.get(DataType.YearMonth))
    cell.setCellType(CellType.NUMERIC)
  }


  def fill(d: Year)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d.getValue)
    cell.setCellStyle(registry.get(DataType.YearMonth))
    cell.setCellType(CellType.NUMERIC)
  }


  def fill(d: MonthDay)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(java.sql.Date.valueOf(d.atYear(2000)))
    cell.setCellStyle(registry.get(DataType.MonthDay))
    cell.setCellType(CellType.NUMERIC)
  }

  def fill(d: java.sql.Time)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellType(CellType.NUMERIC)
    cell.setCellStyle(registry.get(DataType.Time))
  }

  def fill(d: Float)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellType(CellType.NUMERIC)
    cell.setCellStyle(registry.get(DataType.Float))
  }

  def fill(d: Double)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellType(CellType.NUMERIC)
    cell.setCellStyle(registry.get(DataType.Double))
  }

  def fill(d: Int)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(d)
    cell.setCellType(CellType.NUMERIC)
    cell.setCellStyle(registry.get(DataType.Integer))
  }

  def fill(s: String)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(new XSSFRichTextString(s))
    cell.setCellType(CellType.STRING)
  }

  def fill(b: Boolean)(implicit registry: ExcelStyleRegistry): Unit = {
    cell.setCellValue(if (b) "Y" else "N")
    cell.setCellType(CellType.BOOLEAN)
  }

  def fillBlank(): Unit = {
    cell.setCellType(CellType.BLANK)
  }
}
