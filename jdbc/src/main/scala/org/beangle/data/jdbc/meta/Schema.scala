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

package org.beangle.data.jdbc.meta

import org.beangle.commons.lang.Strings
import org.beangle.commons.regex.AntPathPattern
import org.beangle.data.jdbc.meta.Schema.NameFilter

import scala.collection.mutable

object Schema {
  object NameFilter {
    def apply(includes: String, excludes: String = ""): NameFilter = {
      val filter = new NameFilter(true)
      if (Strings.isNotBlank(includes)) {
        Strings.split(includes.trim.toLowerCase()) foreach { p =>
          filter.include(p)
        }
      }
      if (Strings.isNotBlank(excludes)) {
        Strings.split(excludes.trim.toLowerCase()) foreach { p =>
          filter.exclude(p)
        }
      }
      filter
    }
  }

  class NameFilter(lowercase: Boolean = true) {
    val includes = new collection.mutable.ListBuffer[AntPathPattern]
    val excludes = new collection.mutable.ListBuffer[AntPathPattern]

    def filter(tables: Iterable[Identifier]): List[Identifier] = {
      val results = new collection.mutable.ListBuffer[Identifier]
      for (tabId <- tables) {
        val tabame = if (lowercase) tabId.value.toLowerCase else tabId.value
        val tableName = if (tabame.contains(".")) Strings.substringAfter(tabame, ".") else tabame
        if isMatched(tableName) then results += tabId
      }
      results.toList
    }

    def isMatched(name: String): Boolean = {
      includes.exists(_.matches(name)) && !excludes.exists(_.matches(name))
    }

    def exclude(table: String): Unit = {
      excludes += new AntPathPattern(if lowercase then table.toLowerCase else table)
    }

    def include(table: String): Unit = {
      includes += new AntPathPattern(if lowercase then table.toLowerCase else table)
    }
  }
}

class Schema(var database: Database, var name: Identifier) {

  var catalog: Option[Identifier] = None

  assert(null != name)

  val tables = new mutable.HashMap[Identifier, Table]

  val views = new mutable.HashMap[Identifier, View]

  val sequences = new mutable.HashSet[Sequence]

  def hasQuotedIdentifier: Boolean = {
    tables.exists(_._2.hasQuotedIdentifier)
  }

  def cleanEmptyTables(): Unit = {
    tables.filterInPlace((_, table) => table.columns.nonEmpty)
  }

  def cleanEmptyViews(): Unit = {
    views.filterInPlace((_, v) => v.columns.nonEmpty)
  }

  def addTable(table: Table): this.type = {
    tables.put(table.name, table)
    this
  }

  def addView(view: View): this.type = {
    views.put(view.name, view)
    this
  }

  def getOrCreateTable(tbname: String): Table = {
    val tableId = database.engine.toIdentifier(tbname)
    tables.get(tableId) match {
      case Some(table) => table
      case None =>
        val ntable = new Table(this, tableId)
        tables.put(tableId, ntable)
        ntable
    }
  }

  def createTable(tbname: String): Table = {
    val tableId = database.engine.toIdentifier(tbname)
    tables.get(tableId) match {
      case Some(table) =>
        if (table.phantom) {
          table
        } else {
          throw new RuntimeException("Table " + table.qualifiedName + s" is existed,creation aborted.")
        }
      case None =>
        val ntable = new Table(this, tableId)
        tables.put(tableId, ntable)
        ntable
    }
  }

  def createView(vName: String): View = {
    val viewId = database.engine.toIdentifier(vName)
    views.get(viewId) match {
      case Some(v) =>
        throw new RuntimeException("View " + v.qualifiedName + s" is existed,creation aborted.")
      case None =>
        val nv = new View(this, viewId)
        views.put(viewId, nv)
        nv
    }
  }

  /**
   * Using table literal (with or without schema) search table
   */
  def getTable(tbname: String): Option[Table] = {
    val engine = database.engine
    if (tbname.contains(".")) {
      if (name != engine.toIdentifier(Strings.substringBefore(tbname, "."))) None
      else tables.get(engine.toIdentifier(Strings.substringAfter(tbname, ".")))
    } else {
      tables.get(engine.toIdentifier(tbname))
    }
  }

  def getView(tbname: String): Option[View] = {
    val engine = database.engine
    if (tbname.contains(".")) {
      if (name != engine.toIdentifier(Strings.substringBefore(tbname, "."))) None
      else views.get(engine.toIdentifier(Strings.substringAfter(tbname, ".")))
    } else {
      views.get(engine.toIdentifier(tbname))
    }
  }

  def filterTables(includes: Seq[String], excludes: Seq[String]): Seq[Table] = {
    val filter = new NameFilter()
    for (include <- includes) filter.include(include)
    for (exclude <- excludes) filter.exclude(exclude)

    filter.filter(tables.keySet).map { t => tables(t) }
  }

  def filterViews(includes: Seq[String], excludes: Seq[String]): Seq[View] = {
    val filter = new NameFilter()
    for (include <- includes) filter.include(include)
    for (exclude <- excludes) filter.exclude(exclude)

    filter.filter(views.keySet).map { t => views(t) }
  }

  def filterSequences(includes: Seq[String], excludes: Seq[String]): Seq[Sequence] = {
    val engine = database.engine
    val filter = new NameFilter()
    for (include <- includes) filter.include(include)
    for (exclude <- excludes) filter.exclude(exclude)
    val seqMap = sequences.map(f => (f.name, f)).toMap
    filter.filter(seqMap.keys).map { s => seqMap(s) }
  }

  def findTables(name: String): Seq[Table] = {
    val filter = NameFilter(name)
    filter.filter(tables.keySet).map { t => tables(t) }
  }

  override def toString: String = {
    "Schema " + name
  }
}
