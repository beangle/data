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

import java.io.InputStream
import java.text.NumberFormat

import org.apache.poi.hssf.usermodel.{ HSSFCell, HSSFSheet, HSSFWorkbook }
import org.apache.poi.ss.usermodel.{ Cell, DateUtil }
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.apache.poi.ss.usermodel.CellType
import org.beangle.data.transfer.io.ItemReader
import org.beangle.data.transfer.Format

object ExcelItemReader {
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

  this.headIndex = 0
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
    this.dataIndex = this.headIndex + 1
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
      readLine(workbook.getSheetAt(0), headIndex);
    }
  }

  override def readTitle(): List[String] = {
    if (workbook.getNumberOfSheets() < 1) {
      List.empty
    } else {
      val comments = readComments(workbook.getSheetAt(0), headIndex)
      attrCount = comments.length
      comments
    }
  }

  /**
   * 遇到空白单元格停止的读行操作
   */
  protected def readLine(sheet: HSSFSheet, rowIndex: Int): List[String] = {
    val row = sheet.getRow(rowIndex)
    val attrList = new collection.mutable.ListBuffer[String]
    var hasEmptyCell = false
    for (i <- 0 until row.getLastCellNum; if !hasEmptyCell) {
      val cell = row.getCell(i);
      val attr = cell.getRichStringCellValue().getString();
      if (Strings.isEmpty(attr)) {
        hasEmptyCell = true
      } else {
        attrList += attr.trim
      }
    }
    attrList.toList
  }

  /**
   * 读取注释
   */
  def readComments(sheet: HSSFSheet, rowIndex: Int): List[String] = {
    val row = sheet.getRow(rowIndex)
    val attrList = new collection.mutable.ListBuffer[String]
    var hasEmptyCell = false
    for (i <- 0 until row.getLastCellNum; if !hasEmptyCell) {
      val cell = row.getCell(i)
      val comment = cell.getCellComment()
      if (null == comment || Strings.isEmpty(comment.getString().getString())) {
        hasEmptyCell = true
      } else {
        var commentStr = comment.getString().getString();
        if (commentStr.indexOf(':') > 0) {
          commentStr = Strings.substringAfterLast(commentStr, ":")
        }
        attrList += commentStr.trim()
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
    cell.getCellTypeEnum match {
      case CellType.BLANK  => null
      case CellType.STRING => Strings.trim(cell.getRichStringCellValue().getString());
      case CellType.NUMERIC =>
        if (DateUtil.isCellDateFormatted(cell)) {
          cell.getDateCellValue();
        } else {
          ExcelItemReader.numberFormat.format(cell.getNumericCellValue());
        }
      case CellType.BOOLEAN => if (cell.getBooleanCellValue()) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
      case _                => null
    }
  }

  override def format: Format.Value = {
    Format.Xls
  }

  override def close(): Unit = {
    this.workbook.cloneSheet(sheetNum);
  }

}
