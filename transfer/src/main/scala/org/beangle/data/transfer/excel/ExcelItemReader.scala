/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.transfer.excel;

import java.io.InputStream
import java.text.NumberFormat

import org.apache.poi.hssf.usermodel.{ HSSFCell, HSSFSheet, HSSFWorkbook }
import org.apache.poi.ss.usermodel.{ Cell, DateUtil }
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.transfer.io.{ ItemReader, TransferFormat }

object ExcelItemReader {
  val DEFAULT_HEADINDEX = 0
  /** Constant <code>numberFormat</code> */
  val numberFormat = NumberFormat.getInstance()
  numberFormat.setGroupingUsed(false);

}
/**
 * Excel的每行一条数据的读取器
 *
 * @author chaostone
 */
class ExcelItemReader(is: InputStream) extends ItemReader with Logging {

  /** Constant <code>sheetNum=0</code> */
  val sheetNum = 0;

  this.headIndex = ExcelItemReader.DEFAULT_HEADINDEX
  this.dataIndex = headIndex + 1

  /**
   * 下一个要读取的位置 标题行和代码行分别默认占据0,1
   */
  private var indexInSheet: Int = dataIndex;

  /**
   * 属性的个数，0表示在读取值的是否不做读限制
   */
  private var attrCount: Int = _

  /**
   * 读取的工作表
   */
  private val workbook: HSSFWorkbook = new HSSFWorkbook(is)

  def this(is: InputStream, headIndex: Int) {
    this(is)
    this.headIndex = headIndex
    this.dataIndex = headIndex + 1
    this.indexInSheet = dataIndex
  }

  /**
   * 描述放在第一行
   *
   * @return an array of String objects.
   */
  override def readDescription(): List[String] = {
    if (workbook.getNumberOfSheets() < 1) {
      List.empty
    } else {
      val sheet = workbook.getSheetAt(0);
      readLine(sheet, 0)
    }
  }

  override def readTitle(): List[String] = {
    if (workbook.getNumberOfSheets() < 1) {
      List.empty
    } else {
      val sheet = workbook.getSheetAt(0);
      val attrs = readLine(sheet, headIndex);
      attrCount = attrs.length;
      attrs
    }
  }

  /**
   * 遇到空白单元格停止的读行操作
   */
  protected def readLine(sheet: HSSFSheet, rowIndex: Int): List[String] = {
    val row = sheet.getRow(rowIndex)
    val attrList = new collection.mutable.ListBuffer[String]
    var hasEmptyCell = false
    (0 until row.getLastCellNum) foreach { i =>
      if (!hasEmptyCell) {
        val cell = row.getCell(i);
        if (null != cell) {
          val attr = cell.getRichStringCellValue().getString();
          if (Strings.isEmpty(attr)) {
            hasEmptyCell = true
          } else {
            attrList += attr.trim
          }
        } else {
          hasEmptyCell = true
        }
      }
    }
    attrList.toList
  }

  override def read(): Any = {
    val sheet = workbook.getSheetAt(sheetNum);
    if (indexInSheet > sheet.getLastRowNum()) { return null; }
    val row = sheet.getRow(indexInSheet);
    indexInSheet += 1;
    // 如果是个空行,返回空记录
    if (row == null) {
      return new Array[Object](attrCount)
    } else {
      val values = new Array[Object](if (attrCount != 0) attrCount else row.getLastCellNum)
      (0 until values.length) foreach { k =>
        values(k) = getCellValue(row.getCell(k));
      }
      values
    }
  }

  /**
   * 取cell单元格中的数据
   */
  def getCellValue(cell: HSSFCell): Object = {
    if ((cell == null)) return null;
    cell.getCellType match {
      case Cell.CELL_TYPE_BLANK  => null
      case Cell.CELL_TYPE_STRING => Strings.trim(cell.getRichStringCellValue().getString());
      case Cell.CELL_TYPE_NUMERIC =>
        if (DateUtil.isCellDateFormatted(cell)) {
          cell.getDateCellValue();
        } else {
          ExcelItemReader.numberFormat.format(cell.getNumericCellValue());
        }
      case Cell.CELL_TYPE_BOOLEAN => if (cell.getBooleanCellValue()) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
      case _                      => null
    }
  }

  override def format: TransferFormat.Value = {
    TransferFormat.Xls;
  }

  override def close(): Unit = {
    this.workbook.cloneSheet(sheetNum);
  }

}
