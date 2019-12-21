package org.beangle.data.transfer.excel

import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.xssf.usermodel.{XSSFCellStyle, XSSFWorkbook}
import org.beangle.commons.collection.Collections

class ExcelStyleRegistry(workbook: XSSFWorkbook) {

  private val dataFormat = workbook.createDataFormat()

  private val styles = Collections.newMap[String, XSSFCellStyle]

  def get(pattern:String): CellStyle={
    styles.get(pattern)match {
      case Some(s)=> s
      case None=>registerFormat(pattern,pattern)
    }
  }

  def registerFormat(name: String, pattern: String): CellStyle = {
    val style = workbook.createCellStyle()
    style.setDataFormat(dataFormat.getFormat(pattern))
    styles.put(name, style)
    style
  }

  registerFormat("yearmonth", "YYYY-MM")
  registerFormat("date", "YYYY-MM-DD")
  registerFormat("time", "HH:MM:SS")
  registerFormat("datetime", "YYYY-MM-DD HH:MM:SS")
  registerFormat("float", "#,##0.00")
  registerFormat("integer", "#,##0")
}
