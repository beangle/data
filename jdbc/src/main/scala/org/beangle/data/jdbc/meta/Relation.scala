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
import org.beangle.data.jdbc.engine.Engine

import scala.collection.mutable.ListBuffer

abstract class Relation(var schema: Schema, var name: Identifier) extends Ordered[Relation], Comment, Cloneable {

  val columns = new ListBuffer[Column]

  var module: Option[String] = None

  def attach(engine: Engine): this.type = {
    columns foreach { col =>
      val st = col.sqlType
      col.sqlType = engine.toType(st.code, st.precision.getOrElse(0), st.scale.getOrElse(0))
      col.defaultValue foreach { v => col.defaultValue = engine.convert(col.sqlType, v) }
      col.name = col.name.attach(engine)
    }
    this.name = this.name.attach(engine)
    this
  }

  def column(columnName: String): Column = {
    columns.find(f => f.name.toLiteral(engine) == columnName).get
  }

  def getColumn(columnName: String): Option[Column] = {
    columns.find(f => f.name.toLiteral(engine) == columnName)
  }

  def columnExits(columnName: Identifier): Boolean = {
    columns.exists(f => f.name == columnName)
  }

  def rename(column: Column, newName: Identifier): Unit = {
    remove(column)
    column.name = newName
    add(column)
  }

  def add(column: Column): Column = {
    remove(column)
    columns += column
    column
  }

  def add(cols: Column*): Unit = {
    cols foreach { col => add(col) }
  }

  def remove(column: Column): Unit = {
    columns.find(_.name == column.name) foreach { c =>
      columns -= c
    }
  }

  def createColumn(name: String, sqlType: SqlType): Column = {
    val egn = engine
    val col = new Column(egn.toIdentifier(name), sqlType)
    this.add(col)
    col
  }

  def createColumn(name: String, typeName: String): Column = {
    val egn = engine
    val col = new Column(egn.toIdentifier(name), egn.toType(typeName))
    this.add(col)
    col
  }

  def clone(newschema: Schema): Relation

  def hasQuotedIdentifier: Boolean = {
    name.quoted || columns.exists(_.name.quoted)
  }

  def engine: Engine = {
    schema.database.engine
  }

  def quotedColumnNames: List[String] = {
    val e = engine
    columns.result().map(_.name.toLiteral(e))
  }

  def qualifiedName: String = {
    Table.qualify(schema, name)
  }

  override def compare(o: Relation): Int = {
    this.qualifiedName.compareTo(o.qualifiedName)
  }

  def commentAndModule: Option[String] = {
    comment match {
      case Some(c) =>
        module match {
          case Some(m) => Some(s"$c@$m")
          case None => comment
        }
      case None => comment
    }
  }

  def updateSchema(newSchema: Schema): Unit = {
    this.schema = newSchema
  }

  def updateCommentAndModule(newComment: String): Unit = {
    if (Strings.isBlank(newComment)) {
      comment = None
      module = None
    } else {
      if (newComment.contains("@")) {
        comment = Some(Strings.substringBefore(newComment, "@"))
        module = Some(Strings.substringAfter(newComment, "@"))
      } else {
        comment = Some(newComment)
        module = None
      }
    }
  }

  override def toString: String = {
    Table.qualify(schema, name)
  }
}
