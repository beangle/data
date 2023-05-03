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

package org.beangle.data.transfer.csv

import org.beangle.data.csv.{CsvFormat, CsvWriter}
import org.beangle.data.transfer.Format
import org.beangle.data.transfer.exporter.ExportContext
import org.beangle.data.transfer.io.ItemWriter

import java.io.{OutputStream, OutputStreamWriter}

class CsvItemWriter(val context: ExportContext, val outputStream: OutputStream) extends ItemWriter {
  val csvw = new CsvWriter(new OutputStreamWriter(outputStream, "utf-8"),
    new CsvFormat.Builder().delimiter('\'').escape(CsvWriter.NoEscapeChar).build())

  override def write(obj: Any): Unit = {
    csvw.write(obj.asInstanceOf[Array[Any]])
  }

  override def writeTitle(titleName: String, data: Any): Unit = {
    write(data)
  }

  override def format: Format = Format.Csv

  override def close(): Unit = csvw.close()
}
