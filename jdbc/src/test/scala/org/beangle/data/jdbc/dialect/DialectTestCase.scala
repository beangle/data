/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.dialect

import scala.collection.JavaConversions._
import java.util.Map

import org.beangle.data.jdbc.meta.Database
import org.beangle.data.jdbc.meta.Table
import org.beangle.commons.logging.Logging

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class DialectTestCase extends FlatSpec with Matchers with Logging {
  protected var dialect: Dialect = _
  protected var database: Database = _

  protected def listTableAndSequences = {
    val tables: Map[String, Table] = database.tables
    for (name <- tables.keySet()) {
      logger.info("table {}", name)
    }

    val seqs = database.sequences
    for (obj <- seqs) {
      logger.info("sequence {}", obj)
    }
  }
}