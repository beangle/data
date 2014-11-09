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
package org.beangle.data.conversion

import java.io.FileInputStream

import org.beangle.commons.logging.Logging
import org.beangle.data.conversion.db.ConstraintConverter
import org.beangle.data.conversion.db.DatabaseWrapper
import org.beangle.data.conversion.db.IndexConverter
import org.beangle.data.conversion.db.SequenceConverter
import org.beangle.data.conversion.impl.DataConverter
import org.beangle.data.jdbc.meta.Constraint
import org.beangle.data.jdbc.meta.Table

import Config.Source

object ConvertReactor extends Logging {

  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Usage: ConvertReactor /path/to/your/conversion.xml");
      return
    }
    val xml = scala.xml.XML.load(new FileInputStream(args(0)))
    new ConvertReactor(Config(xml)).start()
  }
}

class ConvertReactor(val config: Config) {
  var sourceWrapper: DatabaseWrapper = config.source.buildWrapper()
  var targetWrapper: DatabaseWrapper = config.target.buildWrapper()

  def start() = {
    val loadextra = config.source.table.withIndex || config.source.table.withConstraint
    sourceWrapper.database.loadTables(loadextra)
    sourceWrapper.database.loadSequences()
    targetWrapper.database.loadTables(loadextra)
    targetWrapper.database.loadSequences()

    val converters = new collection.mutable.ListBuffer[Converter]

    val dataConverter = new DataConverter(sourceWrapper, targetWrapper, config.maxthreads)
    val tables = filterTables(config.source, sourceWrapper, targetWrapper);
    dataConverter.addAll(tables)

    converters += dataConverter
    if (config.source.table.withIndex) {
      val indexConverter = new IndexConverter(sourceWrapper, targetWrapper)
      indexConverter.tables ++= tables.map(_._2)
      converters += indexConverter
    }

    if (config.source.table.withConstraint) {
      val contraintConverter = new ConstraintConverter(sourceWrapper, targetWrapper)
      contraintConverter.addAll(filterConstraints(tables))
      converters += contraintConverter
    }

    val sequenceConverter = new SequenceConverter(sourceWrapper, targetWrapper)
    val sequences = sourceWrapper.database.sequences
    val finalSequenceNames = config.source.sequence.filter(sequences.map(s => s.name)).toSet
    val finalSequences = sequences.filter(s => finalSequenceNames.contains(s.name))
    sequenceConverter.addAll(finalSequences)
    converters += sequenceConverter

    for (converter <- converters)
      converter.start()
  }

  private def filterTables(source: Source, srcWrapper: DatabaseWrapper, targetWrapper: DatabaseWrapper): List[Tuple2[Table, Table]] = {
    val tablenames = source.table.filter(srcWrapper.database.tables.keySet)
    val tables = new collection.mutable.ListBuffer[Tuple2[Table, Table]]
    for (name <- tablenames) {
      var srcTable = srcWrapper.database.getTable(name).get
      var targetTable = srcTable.clone()
      targetTable.schema = targetWrapper.database.schema
      if (source.table.lowercase) targetTable.lowerCase()
      tables += (srcTable -> targetTable)
    }
    tables.toList
  }

  private def filterConstraints(tables: List[Tuple2[Table, Table]]): List[Constraint] = {
    val contraints = new collection.mutable.ListBuffer[Constraint]
    for (table <- tables)
      contraints ++= table._2.foreignKeys
    contraints.toList
  }

}
