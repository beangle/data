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
package org.beangle.data.transfer.csv

import java.io.LineNumberReader

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.io.ItemReader

/**
  * CsvItemReader class.
  * @author chaostone
  */
class CsvItemReader(reader: LineNumberReader) extends ItemReader with Logging {

  private var indexInCsv = 1

  def readDescription(): List[String] = {
    List.empty
  }

  def readTitle(): List[String] = {
    reader.readLine()
    Strings.split(reader.readLine(), ",").toList
  }

  private def preRead(): Unit = {
    while (indexInCsv < dataIndex) {
      try {
        reader.readLine()
      } catch {
        case e: Throwable => logger.error("read csv", e);
      }
      indexInCsv += 1
    }
  }

  def read(): Any = {
    preRead()
    var curData: String = null
    try {
      curData = reader.readLine()
    } catch {
      case e1: Throwable => logger.error(curData, e1);
    }
    indexInCsv += 1
    if (null == curData) {
      null
    } else {
      Strings.split(curData, ",")
    }
  }

  def setIndex(headIndex: Int, dataIndex: Int): Unit = {
    if (this.dataIndex == this.indexInCsv) {
      this.dataIndex = dataIndex
      this.indexInCsv = dataIndex
    }
    this.headIndex = headIndex
    this.dataIndex = dataIndex
  }

  def format: Format.Value = {
    Format.Csv
  }

  override def close(): Unit = {
    IOs.close(reader)
  }
}
