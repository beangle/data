package org.beangle.data.transfer.excel

import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFWorkbook}
import org.beangle.commons.collection.Collections
import org.beangle.data.transfer.io.DataType
import org.beangle.data.transfer.io.DataType._

class ExcelStyleRegistry(workbook: XSSFWorkbook) {

  private val dataFormat = workbook.createDataFormat()

  private val styles = Collections.newMap[DataType.Value, XSSFCellStyle]

  private val formats = Map(String -> "@", Boolean -> "@", Short -> "0", Integer -> "0", Long -> "#,##0",
    Float -> "#,##0.##", Double -> "#,##0.##",
    Date -> "YYYY-MM-D", Time -> "HH:MM:SS", DateTime -> "YYYY-MM-DD HH:MM:SS",
    YearMonth -> "YYYY-MM", MonthDay -> "MM-DD")

  def defaultFormat(dt: DataType.Value): String = {
    formats(dt)
  }

  def get(dt: DataType.Value): CellStyle = {
    styles(dt)
  }

  def registerFormat(dt: DataType.Value, pattern: String): CellStyle = {
    val style = workbook.createCellStyle()
    style.setDataFormat(dataFormat.getFormat(pattern))
    styles.put(dt, style)
    style
  }

  DataType.values foreach { dt =>
    registerFormat(dt, defaultFormat(dt))
  }

}
