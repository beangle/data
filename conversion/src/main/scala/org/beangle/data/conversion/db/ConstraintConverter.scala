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
import org.beangle.data.jdbc.meta.Constraint
import org.beangle.data.jdbc.meta.ForeignKey

class ConstraintConverter(val source: DatabaseWrapper, val target: DatabaseWrapper) extends Converter with Logging {

  private val contraints = new collection.mutable.ListBuffer[Constraint]

  def addAll(newContraints: Seq[Constraint]) {
    contraints ++= newContraints
  }

  def reset() {

  }

  def start() {
    val watch = new Stopwatch(true)
    logger.info("Start constraint replication...")
    val targetSchema = target.database.schema
    for (contraint <- contraints.sorted) {
      if (contraint.isInstanceOf[ForeignKey]) {
        val fk = contraint.asInstanceOf[ForeignKey]
        val sql = fk.getAlterSql(target.dialect)
        try {
          target.executor.update(sql)
          logger.info("Apply constaint {}", fk.name)
        } catch {
          case e: Exception =>
            logger.warn("Cannot execute {}", sql)
        }
      }
    }
    logger.info("End constraint replication,using {}", watch)
  }
}