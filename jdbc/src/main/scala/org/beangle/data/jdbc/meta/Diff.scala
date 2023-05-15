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

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.Files
import org.beangle.data.jdbc.engine.{AlterTableDialect, Engine}

import java.io.File
import scala.collection.mutable

object Diff {

  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      println("Usage:Diff database1.xml database2.xml /path/to/diff.sql")
      return
    }
    val dbFile1 = new File(args(0))
    val dbFile2 = new File(args(1))
    if (!dbFile1.exists()) {
      println("Cannot load " + dbFile1.getAbsolutePath)
      return
    }
    if (!dbFile2.exists()) {
      println("Cannot load " + dbFile2.getAbsolutePath)
      return
    }

    val db1 = Serializer.fromXml(Files.readString(dbFile1))
    val db2 = Serializer.fromXml(Files.readString(dbFile2))
    val diff = Diff.diff(db1, db2)
    val sqls = Diff.sql(diff)
    Files.writeString(new File(args(2)), sqls.toBuffer.append("").mkString(";\n"))
  }

  def diff(older: Database, newer: Database): DatabaseDiff = {
    if (newer.engine != older.engine) {
      throw new RuntimeException(s"Cannot diff different engines(${newer.engine.name} and ${older.engine.name}).")
    }
    val newSchemaSet = newer.schemas.keySet.map(_.value).toSet
    val oldSchemaSet = older.schemas.keySet.map(_.value).toSet

    val newSchemas = newSchemaSet.diff(oldSchemaSet)
    val removedSchemas = oldSchemaSet.diff(newSchemaSet)
    val updateSchemas = newSchemaSet.intersect(oldSchemaSet)

    val schemaDiffs = Collections.newMap[String, SchemaDiff]
    updateSchemas foreach { s =>
      val oldSchema = older.getOrCreateSchema(s)
      val newSchema = newer.getOrCreateSchema(s)

      val oldTableSet = oldSchema.tables.keySet.map(_.value).toSet
      val newTableSet = newSchema.tables.keySet.map(_.value).toSet
      val newTables = newTableSet.diff(oldTableSet)
      val removedTables = oldTableSet.diff(newTableSet)
      val updateTables = newTableSet.intersect(oldTableSet)
      val tableDiffs = Collections.newMap[String, TableDiff]
      updateTables foreach { t =>
        val oldT = oldSchema.getTable(t).orNull
        val newT = newSchema.getTable(t).orNull
        diff(oldT, newT, newer.engine) foreach { td =>
          tableDiffs.put(t, td)
        }
      }

      if (!(newTables.isEmpty && removedTables.isEmpty && tableDiffs.isEmpty)) {
        val schemaDiff = new SchemaDiff(oldSchema, newSchema)
        schemaDiff.tableDiffs = tableDiffs.toMap
        schemaDiff.tables = NameDiff(newTables, removedTables, Set.empty, tableDiffs.keySet.toSet)
        schemaDiffs.put(s, schemaDiff)
      }
    }
    val dbDiff = new DatabaseDiff(older, newer)
    if (!(newSchemas.isEmpty && removedSchemas.isEmpty && schemaDiffs.isEmpty)) {
      dbDiff.schemas = NameDiff(newSchemas, removedSchemas, Set.empty, schemaDiffs.keySet.toSet)
      dbDiff.schemaDiffs = schemaDiffs.toMap
    }
    dbDiff
  }

  protected[meta] def diff(older: Table, newer: Table, engine: Engine): Option[TableDiff] = {
    val table = new TableDiff(older, newer)
    if (newer.primaryKey != older.primaryKey) {
      table.hasPrimaryKey = true
    }
    if (newer.comment != older.comment) {
      table.hasComment = true
    }
    val oldColMap = older.columns.map(c => (c.name.toLiteral(engine), c)).toMap
    val newColMap = newer.columns.map(c => (c.name.toLiteral(engine), c)).toMap
    val columnDiff = nameDiff(oldColMap.keySet, newColMap.keySet, oldColMap, newColMap)
    val renameColumnPairs = new mutable.HashSet[(String, String)]
    columnDiff.newer foreach { nc =>
      val newColumn = newer.column(nc)
      older.columns.find(x => x.comment == newColumn.comment) foreach { oldColumn =>
        if (columnDiff.removed.contains(oldColumn.name.toLiteral(engine))) {
          renameColumnPairs += (oldColumn.name.toLiteral(engine) -> newColumn.name.toLiteral(engine))
        }
      }
    }
    if (renameColumnPairs.nonEmpty) {
      table.columns = NameDiff(columnDiff.newer -- renameColumnPairs.map(_._2),
        columnDiff.removed -- renameColumnPairs.map(_._1),
        renameColumnPairs.toSet, columnDiff.updated)
    } else {
      table.columns = columnDiff
    }
    val oldUkMap = older.uniqueKeys.map(c => (c.name.toLiteral(engine), c)).toMap
    val newUkMap = newer.uniqueKeys.map(c => (c.name.toLiteral(engine), c)).toMap
    table.uniqueKeys = nameDiff(oldUkMap.keySet, newUkMap.keySet, oldUkMap, newUkMap)

    val oldFkMap = older.foreignKeys.map(c => (c.name.toLiteral(engine), c)).toMap
    val newFkMap = newer.foreignKeys.map(c => (c.name.toLiteral(engine), c)).toMap
    table.foreignKeys = nameDiff(oldFkMap.keySet, newFkMap.keySet, oldFkMap, newFkMap)

    val oldIdxMap = older.indexes.map(c => (c.name.toLiteral(engine), c)).toMap
    val newIdxMap = newer.indexes.map(c => (c.name.toLiteral(engine), c)).toMap
    table.indexes = nameDiff(oldIdxMap.keySet, newIdxMap.keySet, oldIdxMap, newIdxMap)

    if (table.isEmpty) None else Some(table)
  }

  private def nameDiff(oldNames: Set[String], newNames: Set[String],
                       oldDatas: collection.Map[String, Any],
                       newDatas: collection.Map[String, Any]): NameDiff = {
    val updated = oldNames.intersect(newNames) filter (n => newDatas(n) != oldDatas(n))
    NameDiff(newNames.diff(oldNames), oldNames.diff(newNames), Set.empty, updated)
  }

  def sql(diff: DatabaseDiff): Iterable[String] = {
    if (diff.isEmpty) return List.empty

    val schemaDdl = Collections.newBuffer[String]
    val dropTableDdl = Collections.newBuffer[String]
    val newTableDdl = Collections.newBuffer[String]
    val columnDdl = Collections.newBuffer[String]

    val primaryKeyDdl = Collections.newBuffer[String]
    val foreignKeyDdl = Collections.newBuffer[String]
    val uniqueKeyDdl = Collections.newBuffer[String]
    val indexDdl = Collections.newBuffer[String]
    val commentDdl = Collections.newBuffer[String]

    val engine = diff.newer.engine
    diff.schemas.newerList foreach { n =>
      schemaDdl += s"""create schema $n"""
    }
    diff.schemas.removedList foreach { n =>
      schemaDdl += s"DROP schema $n cascade"
    }
    diff.schemaDiffs.keys.toList.sorted foreach { schema =>
      val sdf = diff.schemaDiffs(schema)
      sdf.tables.removedList foreach { t =>
        dropTableDdl += engine.dropTable(diff.older.getTable(schema, t).get.qualifiedName)
      }
      sdf.tables.newerList foreach { t =>
        val tb = diff.newer.getTable(schema, t).get
        newTableDdl += engine.createTable(tb)
        commentDdl ++= engine.commentsOnTable(tb, false)
        val alter = engine.alterTable(tb)
        tb.primaryKey foreach { pk => primaryKeyDdl += alter.addPrimaryKey(pk) }
        tb.uniqueKeys foreach { uk => uniqueKeyDdl += alter.addUnique(uk) }
        tb.indexes foreach { idx => indexDdl += engine.createIndex(idx) }
        tb.foreignKeys foreach { fk => foreignKeyDdl += alter.addForeignKey(fk) }
      }
      sdf.tableDiffs.keys.toList.sorted foreach { t =>
        val tdf = sdf.tableDiffs(t)
        val alter = engine.alterTable(tdf.newer)
        if (tdf.hasComment) {
          commentDdl ++= engine.commentOnTable(tdf.older.qualifiedName, tdf.newer.comment)
        }
        tdf.columns.removedList foreach { c =>
          columnDdl += alter.dropColumn(tdf.older.column(c))
        }
        tdf.columns.newerList foreach { c =>
          columnDdl ++= alter.addColumn(tdf.newer.column(c))
        }
        tdf.columns.renamed foreach { case (o, n) =>
          val oCol = tdf.older.column(o)
          val nCol = tdf.newer.column(n)
          columnDdl += alter.renameColumn(oCol, nCol.name.toLiteral(engine))
          alterColumn(engine, alter, tdf.newer, oCol, nCol, columnDdl, commentDdl)
        }
        tdf.columns.updatedList foreach { c =>
          val oCol = tdf.older.column(c)
          val nCol = tdf.newer.column(c)
          alterColumn(engine, alter, tdf.newer, oCol, nCol, columnDdl, commentDdl)
        }
        if (tdf.hasPrimaryKey) {
          if tdf.older.primaryKey.nonEmpty then primaryKeyDdl += alter.dropPrimaryKey(tdf.older.primaryKey.get)
          if tdf.newer.primaryKey.nonEmpty then primaryKeyDdl += alter.addPrimaryKey(tdf.newer.primaryKey.get)
        }

        // remove old forignkeys
        tdf.foreignKeys.removedList foreach { fk =>
          foreignKeyDdl += alter.dropConstraint(fk)
        }
        tdf.foreignKeys.updatedList foreach { fk =>
          foreignKeyDdl += alter.dropConstraint(fk)
          foreignKeyDdl += alter.addForeignKey(tdf.newer.getForeignKey(fk).get)
        }
        tdf.foreignKeys.newerList foreach { fk =>
          foreignKeyDdl += alter.addForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        // remove old uniquekeys
        tdf.uniqueKeys.removedList foreach { uk =>
          uniqueKeyDdl += alter.dropConstraint(uk)
        }
        tdf.uniqueKeys.updatedList foreach { uk =>
          uniqueKeyDdl += alter.dropConstraint(uk)
          uniqueKeyDdl += alter.addUnique(tdf.newer.getUniqueKey(uk).get)
        }
        tdf.uniqueKeys.newerList foreach { uk =>
          uniqueKeyDdl += alter.addUnique(tdf.newer.getUniqueKey(uk).get)
        }

        //remove old index
        tdf.indexes.removedList foreach { idx =>
          indexDdl += engine.dropIndex(tdf.older.getIndex(idx).get)
        }
        tdf.indexes.updatedList foreach { idx =>
          indexDdl += engine.dropIndex(tdf.older.getIndex(idx).get)
          indexDdl += engine.createIndex(tdf.newer.getIndex(idx).get)
        }
        tdf.indexes.newerList foreach { idx =>
          indexDdl += engine.createIndex(tdf.newer.getIndex(idx).get)
        }
      }
    }
    val result = Collections.newBuffer[String]
    result ++= schemaDdl
    result ++= dropTableDdl
    result ++= newTableDdl
    result ++= columnDdl
    result ++= primaryKeyDdl
    result ++= foreignKeyDdl
    result ++= uniqueKeyDdl
    result ++= indexDdl
    result ++= commentDdl
    result
  }

  private def alterColumn(engine: Engine, alter: AlterTableDialect, table: Table, oCol: Column, nCol: Column,
                          columnDdl: mutable.Buffer[String], commentDdl: mutable.Buffer[String]): Unit = {
    if (nCol.sqlType.name != oCol.sqlType.name) {
      columnDdl += alter.modifyColumnType(oCol, nCol.sqlType)
    }
    if (!nCol.defaultValue.getOrElse("").equalsIgnoreCase(oCol.defaultValue.getOrElse(""))) {
      columnDdl += alter.modifyColumnDefault(oCol, nCol.defaultValue)
    }
    if (nCol.nullable != oCol.nullable) {
      if nCol.nullable then columnDdl += alter.modifyColumnDropNotNull(nCol)
      else columnDdl += alter.modifyColumnSetNotNull(nCol)
    }
    if (nCol.comment != oCol.comment) {
      commentDdl ++= engine.commentOnColumn(table, nCol, nCol.comment)
    }
    // ignore check and unique,using constrants
  }

}

class DatabaseDiff(val older: Database, val newer: Database) {
  var schemas: NameDiff = NameDiff(Set.empty, Set.empty, Set.empty, Set.empty)
  var schemaDiffs: Map[String, SchemaDiff] = Map.empty

  def isEmpty: Boolean = {
    (schemaDiffs == null || schemaDiffs.isEmpty) && (schemas == null || schemas.isEmpty)
  }
}

class SchemaDiff(val older: Schema, val newer: Schema) {
  var tables: NameDiff = _
  var tableDiffs: Map[String, TableDiff] = _
}

case class NameDiff(newer: Set[String], removed: Set[String], renamed: Set[(String, String)],
                    updated: Set[String]) {
  def isEmpty: Boolean = {
    newer.isEmpty && removed.isEmpty && updated.isEmpty && renamed.isEmpty
  }

  def newerList: List[String] = newer.toList.sorted

  def removedList: List[String] = removed.toList.sorted

  def updatedList: List[String] = updated.toList.sorted
}

class TableDiff(val older: Table, val newer: Table) {
  var hasPrimaryKey: Boolean = _
  var hasComment: Boolean = _
  var columns: NameDiff = _
  var uniqueKeys: NameDiff = _
  var foreignKeys: NameDiff = _
  var indexes: NameDiff = _

  def isEmpty: Boolean = {
    !hasPrimaryKey && !hasComment && columns.isEmpty && uniqueKeys.isEmpty && foreignKeys.isEmpty && indexes.isEmpty
  }
}
