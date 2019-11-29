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

import scala.xml.Utility.escape

class Serializer(db: Database) {

  private val engine = db.engine

  def toXML(): String = {
    val sb = new StringBuilder
    sb ++= "<db>"
    if (db.schemas.nonEmpty) {
      sb ++= "<schemas>"
      db.schemas foreach { case (name, schema) =>
        sb ++= s"""<schema name="$name">"""
        if (schema.tables.nonEmpty) {
          sb ++= "<tables>"
          schema.tables foreach { case (i, table) =>
            sb ++= toXml(table, db.engine)
          }
          sb ++= "</tables>"
        }
        sb ++= "</schema>"
      }
      sb ++= "</schemas>"

    }
    sb ++= "</db>"
    sb.mkString
  }

  def toXml(table: Table, engine: Engine): String = {
    val sb = new StringBuilder
    sb ++= s"""<table name="${table.name.toLiteral(engine)}""""
    table.comment foreach { c =>
      sb ++= s""" comment="$c" """
    }
    sb ++= ">"
    sb ++= "<columns>"
    table.columns foreach { col =>
      sb ++= toXml(col)
    }
    sb ++= "</columns>"
    table.primaryKey foreach { pk =>
      sb ++= "<primary-key"
      if (null != pk.name && !pk.name.value.isBlank) {
        sb ++= s""" name="${name(pk.name)}" """
      }
      sb ++= s""" columns="${collectNames(pk.columns)}"/>"""
    }
    if (table.foreignKeys.nonEmpty) {
      sb ++= "<foreign-keys>"
      table.foreignKeys foreach { fk =>
        sb ++= toXml(fk)
      }
      sb ++= "</foreign-keys>"
    }
    if (table.uniqueKeys.nonEmpty) {
      sb ++= "<unique-keys>"
      table.uniqueKeys foreach { uk =>
        sb ++= toXml(uk)
      }
      sb ++= "</unique-keys>"
    }
    if (table.indexes.nonEmpty) {
      sb ++= "<indexes>"
      table.indexes foreach { idx =>
        sb ++= toXml(idx)
      }
      sb ++= "</indexes>"
    }
    sb ++= "</table>"
    sb.mkString
  }

  def collectNames(cols: Iterable[Identifier]): String = {
    cols.map(name).mkString(",")
  }

  def toXml(col: Column): String = {
    val sb = new StringBuilder
    sb ++= s"""<column name="${name(col.name)}""""
    sb ++= s""" type="${col.sqlType.name}""""
    if (!col.nullable) {
      sb ++= """ nullable="false""""
    }
    if (col.unique) {
      sb ++= """ unique="true""""
    }
    col.check foreach { c =>
      sb ++= s""" check="$c""""
    }
    col.defaultValue foreach { c =>
      sb ++= s""" default="$c""""
    }
    col.comment foreach { c =>
      sb ++= s""" comment="${escape(c)}""""
    }
    sb ++= "/>"
    sb.mkString
  }


  def toXml(fk: ForeignKey): String = {
    val sb = new StringBuilder(
      s"""<foreign-key name="${name(fk.name)}" columns="${collectNames(fk.columns)}" """)
    sb ++= s""" referenced-table="${fk.referencedTable.qualifiedName}" referenced-columns="${collectNames(fk.referencedColumns)}" """
    if (fk.cascadeDelete) {
      sb ++= """ cascade-delete="true" """
    }
    if (!fk.enabled) {
      sb ++= """ enabled="false" """
    }
    sb ++= "/>"
    sb.mkString
  }

  def toXml(uk: UniqueKey): String = {
    val sb = new StringBuilder(
      s"""<unique-key name="${name(uk.name)}" """)
    sb ++= s""" columns="${collectNames(uk.columns)}" """
    if (!uk.enabled) {
      sb ++= """ enabled="false" """
    }
    sb ++= "/>"
    sb.mkString
  }

  def toXml(idx: Index): String = {
    val sb = new StringBuilder(
      s"""<index name="${name(idx.name)}" """)
    sb ++= s""" columns="${collectNames(idx.columns)}" """
    if (idx.unique) {
      sb ++= """ unique="true" """
    }
    sb ++= "/>"
    sb.mkString
  }

  def name(i: Identifier): String = {
    escape(i.toLiteral(engine))
  }
}
