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
package org.beangle.data.jdbc.meta

import java.io.File

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.Files
import org.beangle.data.jdbc.engine.Engine

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

    val sb = Collections.newBuffer[String]
    val engine = diff.newer.engine
    diff.schemas.newer foreach { n =>
      sb += s"""create schema $n"""
    }
    diff.schemas.removed foreach { n =>
      sb += s"DROP schema $n cascade"
    }
    diff.schemaDiffs foreach { case (schema, sdf) =>
      sdf.tables.removed foreach { t =>
        sb += engine.dropTable(diff.older.getTable(schema, t).get.qualifiedName)
      }
      sdf.tables.newer foreach { t =>
        val tb = diff.newer.getTable(schema, t).get
        sb += engine.createTable(tb)
        sb ++= engine.commentsOnTable(tb,false)
        tb.primaryKey foreach { pk =>
          sb += engine.alterTableAddPrimaryKey(tb, pk)
        }

        tb.uniqueKeys foreach { uk =>
          sb += engine.alterTableAddUnique(uk)
        }

        tb.indexes foreach { idx =>
          sb += engine.createIndex(idx)
        }

        tb.foreignKeys foreach { fk =>
          sb += engine.alterTableAddForeignKey(fk)
        }
      }
      sdf.tableDiffs foreach { case (_, tdf) =>
        if (tdf.hasComment) {
          sb ++= engine.commentOnTable(tdf.older.qualifiedName, tdf.newer.comment)
        }
        tdf.columns.removed foreach { c =>
          sb += engine.alterTableDropColumn(tdf.older, tdf.older.column(c))
        }
        tdf.columns.newer foreach { c =>
          sb ++= engine.alterTableAddColumn(tdf.newer, tdf.newer.column(c))
        }
        tdf.columns.renamed foreach { case (o, n) =>
          val oCol = tdf.older.column(o)
          val nCol = tdf.newer.column(n)

          if (nCol.sqlType != oCol.sqlType) {
            sb += engine.alterTableModifyColumnType(tdf.older, oCol, nCol.sqlType)
          }
          if (nCol.defaultValue != oCol.defaultValue) {
            sb += engine.alterTableModifyColumnDefault(tdf.older, oCol, nCol.defaultValue)
          }
          if (nCol.nullable != oCol.nullable) {
            if (nCol.nullable) {
              sb += engine.alterTableModifyColumnDropNotNull(tdf.newer, nCol)
            } else {
              sb += engine.alterTableModifyColumnSetNotNull(tdf.newer, nCol)
            }
          }
          sb += engine.alterTableRenameColumn(tdf.older, oCol, nCol.name.toLiteral(engine))
        }
        tdf.columns.updated foreach { c =>
          val oCol = tdf.older.column(c)
          val nCol = tdf.newer.column(c)
          if (nCol.sqlType != oCol.sqlType) {
            sb += engine.alterTableModifyColumnType(tdf.older, oCol, nCol.sqlType)
          }
          if (nCol.defaultValue != oCol.defaultValue) {
            sb += engine.alterTableModifyColumnDefault(tdf.older, oCol, nCol.defaultValue)
          }
          if (nCol.nullable != oCol.nullable) {
            if (nCol.nullable) {
              sb += engine.alterTableModifyColumnDropNotNull(tdf.newer, nCol)
            } else {
              sb += engine.alterTableModifyColumnSetNotNull(tdf.newer, nCol)
            }
          }
          if (nCol.comment != oCol.comment) {
            sb ++= engine.commentOnColumn(tdf.older, oCol, nCol.comment)
          }
          // ignore check and unique,using constrants
        }
        if (tdf.hasPrimaryKey) {
          if (tdf.older.primaryKey.nonEmpty) {
            sb += engine.alterTableDropPrimaryKey(tdf.older, tdf.older.primaryKey.get)
          }
          if (tdf.newer.primaryKey.nonEmpty) {
            sb += engine.alterTableAddPrimaryKey(tdf.newer, tdf.newer.primaryKey.get)
          }
        }

        // remove old forignkeys
        tdf.foreignKeys.removed foreach { fk =>
          sb += engine.alterTableDropConstraint(tdf.older, fk)
        }
        tdf.foreignKeys.updated foreach { fk =>
          sb += engine.alterTableDropConstraint(tdf.older, fk)
          sb += engine.alterTableAddForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        tdf.foreignKeys.newer foreach { fk =>
          sb += engine.alterTableAddForeignKey(tdf.newer.getForeignKey(fk).get)
        }

        // remove old uniquekeys
        tdf.uniqueKeys.removed foreach { uk =>
          sb += engine.alterTableDropConstraint(tdf.older, uk)
        }

        tdf.uniqueKeys.updated foreach { uk =>
          sb += engine.alterTableDropConstraint(tdf.older, uk)
          sb += engine.alterTableAddUnique(tdf.newer.getUniqueKey(uk).get)
        }
        tdf.uniqueKeys.newer foreach { uk =>
          sb += engine.alterTableAddUnique(tdf.newer.getUniqueKey(uk).get)
        }

        //remove old index
        tdf.indexes.removed foreach { idx =>
          sb += engine.dropIndex(tdf.older.getIndex(idx).get)
        }

        tdf.indexes.updated foreach { idx =>
          sb += engine.dropIndex(tdf.older.getIndex(idx).get)
          sb += engine.createIndex(tdf.newer.getIndex(idx).get)
        }

        tdf.indexes.newer foreach { idx =>
          sb += engine.createIndex(tdf.newer.getIndex(idx).get)
        }
      }
    }
    sb
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
