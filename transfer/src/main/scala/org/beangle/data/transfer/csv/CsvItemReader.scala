/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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
package org.beangle.data.transfer.csv;

import java.io.IOException
import java.io.LineNumberReader
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import org.beangle.data.transfer.io.ItemReader
import org.beangle.data.transfer.io.TransferFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory;
import org.beangle.commons.logging.Logging

/**
 * CsvItemReader class.
 *
 * @author chaostone
 */
class CsvItemReader(reader: LineNumberReader) extends ItemReader with Logging {

  private var indexInCsv = 1;

  def readDescription(): List[String] = {
    List.empty
  }

  def readTitle(): List[String] = {
    reader.readLine();
    return Strings.split(reader.readLine(), ",").toList
  }

  private def preRead(): Unit = {
    while (indexInCsv < dataIndex) {
      try {
        reader.readLine();
      } catch {
        case e: Throwable => logger.error("read csv", e);
      }
      indexInCsv += 1;
    }
  }

  def read(): Any = {
    preRead();
    var curData: String = null;
    try {
      curData = reader.readLine();
    } catch {
      case e1: Throwable => logger.error(curData, e1);
    }
    indexInCsv += 1;
    if (null == curData) {
      return null;
    } else {
      return Strings.split(curData, ",")
    }
  }

  def setIndex(headIndex: Int, dataIndex: Int) {
    if (this.dataIndex == this.indexInCsv) {
      this.dataIndex = dataIndex;
      this.indexInCsv = dataIndex;
    }
    this.headIndex = headIndex
    this.dataIndex = dataIndex
  }

  def format: TransferFormat.Value = {
    TransferFormat.Csv;
  }

  override def close(): Unit = {
    IOs.close(reader);
  }
}
