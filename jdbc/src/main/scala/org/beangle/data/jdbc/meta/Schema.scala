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
package org.beangle.data.jdbc.meta

import scala.collection.mutable
import java.sql.{ SQLException, DatabaseMetaData }
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.dialect.Name

/**
 * JDBC database metadata
 *
 * @author chaostone
 */
class Schema(val dialect: Dialect, val catalog: Name, val name: Name) {

  val tables = new mutable.HashMap[String, Table]

  val sequences = new mutable.HashSet[Sequence]

  assert(null != name)

  def loadTables(meta: DatabaseMetaData, extras: Boolean): mutable.HashMap[String, Table] = {
    val loader = new MetadataLoader(dialect, meta)
    val loadTables: Set[Table] = loader.loadTables(catalog, name, extras)
    for (table <- loadTables) {
      table.dialect = dialect
      tables.put(table.name.value, table)
    }
    tables
  }

  def loadSequences(meta: DatabaseMetaData): mutable.HashSet[Sequence] = {
    sequences ++= new MetadataLoader(dialect, meta).loadSequences(name)
    sequences
  }

  /**
   * Using table literal (with or without schema) search table
   */
  def getTable(name: String): Option[Table] = {
    val nschema = this.name.qualified(dialect)
    if (name.contains(".")) {
      if (nschema != dialect.parse(Strings.substringBefore(name, ".")).value) None
      else tables.get(dialect.parse(Strings.substringAfter(name, ".")).value)
    } else {
      tables.get(dialect.parse(name).value)
    }
  }

  override def toString = "Database" + tables.keySet.toString + sequences.toString

  def filterTables(includes: Seq[String], excludes: Seq[String]): Seq[Table] = {
    val filter = new NameFilter()
    if (null != includes) {
      for (include <- includes) filter.include(dialect.parse(include).value)
    }
    if (null != excludes) {
      for (exclude <- excludes) filter.exclude(dialect.parse(exclude).value)
    }

    filter.filter(tables.keySet).map { t => tables(t) }
  }

  def filterSequences(includes: Seq[String], excludes: Seq[String]): Seq[Sequence] = {
    val filter = new NameFilter()
    if (null != includes) {
      for (include <- includes) filter.include(dialect.parse(include).value)
    }
    if (null != excludes) {
      for (exclude <- excludes) filter.exclude(dialect.parse(exclude).value)
    }
    val seqMap = sequences.map(f => (f.qualifiedName, f)).toMap
    filter.filter(seqMap.keys).map { s => seqMap(s) }
  }

}

class NameFilter {

  val excludes = new collection.mutable.ListBuffer[String]

  val includes = new collection.mutable.ListBuffer[String]

  def filter(tables: Iterable[String]): List[String] = {
    val results = new collection.mutable.ListBuffer[String]
    for (tabame <- tables) {
      val tableName = if (tabame.contains(".")) Strings.substringAfter(tabame, ".") else tabame
      if (includes.exists(p => p == "*" || tableName.startsWith(p) && !excludes.contains(tableName)))
        results += tabame
    }
    results.toList
  }

  def exclude(table: String) {
    excludes += table
  }

  def include(table: String) {
    includes += table
  }
}
