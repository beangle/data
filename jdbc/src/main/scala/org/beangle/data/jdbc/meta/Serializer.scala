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

import org.beangle.commons.lang.Strings.split
import org.beangle.commons.xml.NodeOps.given
import org.beangle.commons.xml.XmlNode
import org.beangle.data.jdbc.engine.Engines

import java.io.StringReader
import scala.language.implicitConversions

object Serializer {

  def fromXml(content: String): Database = {
    val root = scala.xml.XML.load(new StringReader(content))
    val engine = Engines.forName(root.attr("engine"))
    val database = new Database(engine)
    database.version = root.attr("version")
    (root \\ "schema") foreach { schemaElem =>
      val schema = database.getOrCreateSchema(schemaElem.name)
      (schemaElem \ "tables" \ "table") foreach { tableElem =>
        val table = schema.createTable(tableElem.name)
        tableElem.get("comment").foreach(n => table.updateCommentAndModule(n))
        (tableElem \ "columns" \ "column") foreach { colElem =>
          val col = table.createColumn(colElem.name, colElem.attr("type"))
          colElem.get("nullable").foreach(n => col.nullable = n.toBoolean)
          colElem.get("unique").foreach(n => col.unique = n.toBoolean)
          colElem.get("check").foreach(n => col.check = Some(n))
          colElem.get("comment").foreach(n => col.comment = Some(n))
        }
        (tableElem \ "primary-key") foreach { pkElem =>
          table.createPrimaryKey(pkElem.name, split(pkElem.attr("columns")).toSeq: _*)
        }
        (tableElem \ "foreign-keys" \ "foreign-key") foreach { fkElem =>
          val name = fkElem.name
          val column = fkElem.attr("column")
          val referTable = fkElem.attr("referenced-table")
          val referColumn = fkElem.attr("referenced-column")
          val fk = table.createForeignKey(name, column, database.refTable(referTable), referColumn)
          fkElem.get("enabled").foreach(n => fk.enabled = n.toBoolean)
          fkElem.get("cascadeDelete").foreach(n => fk.cascadeDelete = n.toBoolean)
        }
        (tableElem \ "unique-keys" \ "unique-key") foreach { ukElem =>
          val uk = table.createUniqueKey(ukElem.name, split(ukElem.attr("columns")).toSeq: _*)
          ukElem.get("enabled").foreach(n => uk.enabled = n.toBoolean)
        }
        (tableElem \ "indexes" \ "index") foreach { idxElem =>
          var unique = false
          idxElem.get("unique").foreach(n => unique = n.toBoolean)
          table.createIndex(idxElem.name, unique, split(idxElem.attr("columns")).toSeq: _*)
        }
      }
    }
    database
  }

  def toXml(database: Database): String = {
    new Exporter(database).toXml
  }

  private class Exporter(db: Database) {
    def toXml: String = {
      val dbNode = XmlNode("db", ("engine", db.engine.name), ("version", db.version))
      if (db.schemas.nonEmpty) {
        val schemasNode = dbNode.createChild("schemas")
        val schemaNames = db.schemas.keys.toBuffer.sorted
        schemaNames foreach { schemaName =>
          val schema = db.schemas(schemaName)
          if (schema.tables.nonEmpty || schema.sequences.nonEmpty) {
            val schemaNode = schemasNode.createChild("schema", ("name", schemaName))
            if (schema.tables.nonEmpty) {
              val tablesNode = schemaNode.createChild("tables")
              schema.tables.values.toBuffer.sorted foreach { table =>
                appendXml(table, tablesNode)
              }
            }
          }
        }
      }
      dbNode.toXml
    }

    private def appendXml(table: Table, tablesNode: XmlNode): Unit = {
      val tableNode = tablesNode.createChild("table", "name" -> table.name)
      tableNode.attr("comment", table.commentAndModule)
      val columnsNode = tableNode.createChild("columns")
      val columns = table.columns.sortWith((c1, c2) => if (c1.name.value == "id") true else if (c2.name.value == "id") false else c1.name.value.compareTo(c2.name.value) < 0)
      columns foreach { col =>
        val colNode = columnsNode.createChild("column")
        colNode.attr("name", col.name)
        colNode.attr("type", col.sqlType.name)
        if (!col.nullable) {
          colNode.attr("nullable", col.nullable.toString)
        }
        if (col.unique) {
          colNode.attr("unique", col.unique.toString)
        }
        colNode.attr("check", col.check)
        colNode.attr("defaultValue", col.defaultValue)
        colNode.attr("comment", col.comment)
      }
      table.primaryKey foreach { pk =>
        val pkNode = tableNode.createChild("primary-key")
        if (null != pk.name && !pk.name.value.isBlank) {
          pkNode.attr("name", pk.name)
        }
        pkNode.attr("columns", collectNames(pk.columns))
      }
      if (table.foreignKeys.nonEmpty) {
        val fkNodes = tableNode.createChild("foreign-keys")
        table.foreignKeys foreach { fk =>
          val fkNode = fkNodes.createChild("foreign-key", "name" -> fk.name)
          fkNode.attr("column", collectNames(fk.columns))
          fkNode.attr("referenced-table", fk.referencedTable.qualifiedName)
          fkNode.attr("referenced-column", collectNames(fk.referencedColumns))
          if (fk.cascadeDelete) {
            fkNode.attr("cascade-delete", "true")
          }
          if (!fk.enabled) {
            fkNode.attr("enabled", "false")
          }
        }
      }
      if (table.uniqueKeys.nonEmpty) {
        val ukNodes = tableNode.createChild("unique-keys")
        table.uniqueKeys foreach { uk =>
          val ukNode = ukNodes.createChild("unique-key", "name" -> uk.name)
          ukNode.attr("columns", collectNames(uk.columns))
          if (!uk.enabled) {
            ukNode.attr("enabled", uk.enabled.toString)
          }
        }
      }
      if (table.indexes.nonEmpty) {
        val idxNodes = tableNode.createChild("indexes")
        table.indexes foreach { idx =>
          val idxNode = idxNodes.createChild("index", "name" -> idx.name)
          idxNode.attr("columns", collectNames(idx.columns))
          if (idx.unique) {
            idxNode.attr("unique", idx.unique.toString)
          }
        }
      }
    }

    def collectNames(cols: Iterable[Identifier]): String = {
      cols.map(_.toLiteral(db.engine)).mkString(",")
    }

    implicit def identifier2String(i: Identifier): String = {
      i.toLiteral(db.engine)
    }
  }

}
