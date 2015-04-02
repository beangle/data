/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.conversion.db

import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.conversion.Converter
import org.beangle.data.jdbc.meta.Table

class IndexConverter(val source: DatabaseWrapper, val target: DatabaseWrapper) extends Converter with Logging {

  val tables = new collection.mutable.ListBuffer[Table]

  def reset() {
  }

  def start() {
    val watch = new Stopwatch(true)
    var indexCount = 0;
    for (table <- tables) {
      for (index <- table.indexes) {
        try {
          if (null == table.primaryKey || index.columns != table.primaryKey.columns) {
            indexCount += 1
            target.executor.update(index.createSql(target.dialect))
          }
        } catch {
          case e: Exception =>
            error(s"Cannot create index ${index.name}", e)
        }
      }
    }
    info(s"End $indexCount indexes conversion,using $watch")
  }

}