/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
package org.beangle.data.conversion.impl

import org.beangle.data.conversion.wrapper.DatabaseWrapper
import org.beangle.commons.logging.Logging
import org.beangle.data.conversion.Converter
import org.beangle.data.jdbc.meta.Sequence
import org.beangle.commons.lang.time.Stopwatch

class SequenceConverter(val source: DatabaseWrapper, val target: DatabaseWrapper) extends Converter with Logging {

  val sequences = new collection.mutable.ListBuffer[Sequence]

  def reset() {

  }

  private def reCreate(sequence: Sequence): Boolean = {
    if (target.drop(sequence)) {
      if (target.create(sequence)) {
        logger.info("Recreate sequence {}", sequence.name)
        return true
      } else {
        logger.error("Recreate sequence {} failure.", sequence.name)
      }
    }
    return false
  }

  def start() {
    val targetDialect = target.database.dialect
    if (null == targetDialect.sequenceGrammar) {
      logger.info("Target database {} dosen't support sequence,replication ommited.", targetDialect
        .getClass().getSimpleName())
      return
    }
    val watch = new Stopwatch(true)
    logger.info("Start sequence replication...")
    for (sequence <- sequences.sorted) {
      reCreate(sequence)
    }
    logger.info("End {} sequence replication,using {}", sequences.length, watch)
  }

  def addAll(newSequences: collection.Iterable[Sequence]) {
    sequences ++= newSequences
  }
}