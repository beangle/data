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

import java.text.DecimalFormat

import org.apache.poi.xssf.usermodel.{XSSFCell, XSSFRow, XSSFWorkbook}
import org.beangle.commons.lang.Strings

/**
 * 写到excel中的工具
 *
 * @author chaostone
 */
object ExcelTools {
  val numberformat = new DecimalFormat("#0.00")

  def toExcel(datas: Iterable[Array[_]], propertyShowKeys: String): XSSFWorkbook = {
    toExcel(new XSSFWorkbook(), "sheet1", datas, propertyShowKeys)
  }

  /**
   * 将一个对象数组的集合导出成excel
   */
  def toExcel(wb: XSSFWorkbook, sheetName: String, datas: Iterable[Array[_]],
    propertyShowKeys: String): XSSFWorkbook = {
    val sheet = wb.createSheet(sheetName)
    var row: XSSFRow = null
    var cell: XSSFCell = null

    val pShowKeys = Strings.split(propertyShowKeys, ",")
    row = sheet.createRow(0); // 建立新行
    // 显示标题列名
    pShowKeys.indices foreach { i =>
      cell = row.createCell(i); // 建立新cell
      // cell.setEncoding(XSSFCell.ENCODING_UTF_16);
      cell.setCellValue(pShowKeys(i));
    }
    // 逐行取数
    var rowId = 1; // 数据行号（从2行开始填充)
    datas foreach { objs =>
      row = sheet.createRow(rowId); // 建立新行
      objs.indices foreach { j =>
        cell = row.createCell(j); // 建立新cell
        // cell.setEncoding(XSSFCell.ENCODING_UTF_16);
        cell.setCellValue(if (objs(j) == null) "" else objs(j).toString)
      }
      rowId += 1
    }
    wb
  }
}
