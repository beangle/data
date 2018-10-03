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
package org.beangle.data.transfer.importer

import java.io.InputStream
import java.io.InputStreamReader
import java.io.LineNumberReader

import org.beangle.data.transfer.csv.CsvItemReader
import org.beangle.data.transfer.excel.ExcelItemReader
import org.beangle.data.transfer.Format

/**
 * Importer Factory
 *
 * @author chaostone
 * @since 3.1
 */
object ImporterFactory {

  def getEntityImporter(format: Format.Value, is: InputStream, clazz: Class[_],
                        params: Map[String, Any]): EntityImporter = {
    val importer = new DefaultEntityImporter(clazz);
    if (format.equals(Format.Xls)) {
      importer.reader = new ExcelItemReader(is, 1)
    } else {
      val reader = new LineNumberReader(new InputStreamReader(is))
      importer.reader = new CsvItemReader(reader)
    }
    importer
  }
}
