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

object Table {
  def qualify(schema: Schema, name: Identifier): String = {
    val engine = schema.database.engine
    qualify(schema.name.toLiteral(engine), name.toLiteral(engine))
  }

  def qualify(schema: String, name: String): String = {
    val qualifiedName = new StringBuilder()
    if (Strings.isNotEmpty(schema)) qualifiedName.append(schema).append('.')
    qualifiedName.append(name).toString
  }

  def apply(schema: Schema, name: String): Table = {
    new Table(schema, Identifier(name))
  }
}

class Table(var schema: Schema, var name: Identifier) extends Ordered[Table] with Cloneable with Comment {
  /** 虚拟表 */
  var phantom: Boolean = _
  var primaryKey: Option[PrimaryKey] = None
  val columns = new ListBuffer[Column]
  val uniqueKeys = new ListBuffer[UniqueKey]
  val foreignKeys = new ListBuffer[ForeignKey]
  val indexes = new ListBuffer[Index]

  var module: Option[String] = None

  def engine: Engine = {
    schema.database.engine
  }

  /** has quoted identifier
   *
   * @return
   */
  def hasQuotedIdentifier: Boolean = {
    name.quoted ||
      columns.exists(_.name.quoted) ||
      indexes.exists(_.name.quoted) ||
      uniqueKeys.exists(_.name.quoted) ||
      foreignKeys.exists(_.name.quoted)
  }

  def quotedColumnNames: List[String] = {
    val e = engine
    columns.result().map(_.name.toLiteral(e))
  }

  def qualifiedName: String = {
    Table.qualify(schema, name)
  }

  def attach(engine: Engine): this.type = {
    columns foreach { col =>
      val st = col.sqlType
      col.sqlType = engine.toType(st.code, st.precision.getOrElse(0), st.scale.getOrElse(0))
      col.defaultValue foreach { v => col.defaultValue = engine.convert(col.sqlType, v) }
      col.name = col.name.attach(engine)
    }
    this.name = this.name.attach(engine)
    primaryKey foreach (pk => pk.attach(engine))
    for (fk <- foreignKeys) fk.attach(engine)
    for (uk <- uniqueKeys) uk.attach(engine)
    for (idx <- indexes) idx.attach(engine)
    this
  }

  def clone(newschema: Schema): Table = {
    val t = this.clone()
    val oldSchema = t.schema
    for (fk <- t.foreignKeys) {
      if (fk.referencedTable.schema == oldSchema)
        fk.referencedTable.schema = newschema
    }
    t.schema = newschema
    t.attach(t.engine)
    t
  }

  override def clone(): Table = {
    val tb: Table = new Table(schema, name)
    tb.comment = comment
    tb.module = module
    for (col <- columns) tb.add(col.clone())
    primaryKey foreach { pk =>
      val npk = pk.clone()
      npk.table = tb
      tb.primaryKey = Some(npk)
    }
    for (fk <- foreignKeys) tb.add(fk.clone())
    for (uk <- uniqueKeys) tb.add(uk.clone())
    for (idx <- indexes) tb.add(idx.clone())
    tb
  }

  def isPrimaryKeyIndex(indexName: String): Boolean = {
    primaryKey.exists(_.name.value == indexName) || indexName.toLowerCase.contains("primary_key")
  }

  /** 两个表格是否结构相同
   *
   * @param o
   * @return
   */
  def isSameStruct(o: Table): Boolean = {
    if this.qualifiedName != o.qualifiedName then false
    else if this.columns.size != o.columns.size then false
    else
      this.columns.forall { c =>
        o.columns.find(_.name == c.name) match {
          case None => false
          case Some(c2) => c.isSame(c2)
        }
      }
  }

  def toCase(lower: Boolean): Unit = {
    this.name = name.toCase(lower)
    for (col <- columns) col.toCase(lower)
    primaryKey.foreach(pk => pk.toCase(lower))
    for (fk <- foreignKeys) fk.toCase(lower)
    for (uk <- uniqueKeys) uk.toCase(lower)
    for (idx <- indexes) idx.toCase(lower)
  }

  override def compare(o: Table): Int = {
    this.qualifiedName.compareTo(o.qualifiedName)
  }

  private def hasPrimaryKey: Boolean = {
    primaryKey.isDefined
  }

  override def toString: String = {
    Table.qualify(schema, name)
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

  def getForeignKey(keyName: String): Option[ForeignKey] = {
    foreignKeys.find(f => f.name.toLiteral(engine) == keyName)
  }

  def getUniqueKey(keyName: String): Option[UniqueKey] = {
    uniqueKeys.find(f => f.name.toLiteral(engine) == keyName)
  }

  def createUniqueKey(keyName: String, columnNames: String*): UniqueKey = {
    val eng = engine
    val uk = new UniqueKey(this, Identifier("uk_temp"))
    columnNames foreach { colName =>
      uk.addColumn(eng.toIdentifier(colName))
    }
    if (Strings.isBlank(keyName)) {
      uk.name = eng.toIdentifier(Constraint.autoname(uk))
    } else {
      uk.name = eng.toIdentifier(keyName)
    }
    this.add(uk)
    uk
  }

  def createIndex(indexName: String, unique: Boolean, columnNames: String*): Index = {
    val index = new Index(this, Identifier("indx_temp"))
    val eng = engine
    columnNames foreach { colName =>
      index.addColumn(eng.toIdentifier(colName))
    }
    index.unique = unique
    if (Strings.isBlank(indexName)) {
      index.name = eng.toIdentifier(Constraint.autoname(index))
    } else {
      index.name = eng.toIdentifier(indexName)
    }
    this.indexes += index
    index
  }

  def createPrimaryKey(keyName: String, columnNames: String*): PrimaryKey = {
    val egn = engine
    val pk = if (columnNames.size == 1) {
      new PrimaryKey(this, Identifier.empty, egn.toIdentifier(columnNames.head))
    } else {
      val pk2 = new PrimaryKey(this, Identifier.empty, null)
      columnNames.foreach { cn =>
        val cnName = egn.toIdentifier(cn)
        this.columns foreach (c => if (c.name == cnName) pk2.addColumn(c))
      }
      pk2
    }
    pk.name = engine.toIdentifier(if (Strings.isBlank(keyName)) Constraint.autoname(pk) else keyName)
    this.primaryKey = Some(pk)
    pk.columns foreach { c =>
      this.column(c.toLiteral(engine)).nullable = false
    }
    pk
  }

  def createForeignKey(keyName: String, columnName: String, refTable: TableRef, refencedColumn: String): ForeignKey = {
    val eng = engine
    val fk = new ForeignKey(this, Identifier("fk_temp"), eng.toIdentifier(columnName))
    fk.refer(refTable, eng.toIdentifier(refencedColumn))
    fk.name = if (Strings.isNotBlank(keyName)) {
      engine.toIdentifier(keyName)
    } else {
      engine.toIdentifier(Constraint.autoname(fk))
    }
    this.add(fk)
  }

  def createForeignKey(keyName: String, columnName: String, refTable: Table): ForeignKey = {
    val eng = engine
    refTable.primaryKey match {
      case Some(pk) =>
        val fk = new ForeignKey(this, Identifier("fk_temp"), eng.toIdentifier(columnName))
        fk.refer(refTable, pk.columns.toSeq: _*)
        if (Strings.isBlank(keyName)) {
          fk.name = eng.toIdentifier(Constraint.autoname(fk))
        } else {
          fk.name = eng.toIdentifier(keyName)
        }
        this.add(fk)
      case None =>
        throw new RuntimeException("Cannot refer on a table without primary key")
    }
  }

  def add(key: ForeignKey): ForeignKey = {
    key.table = this
    this.foreignKeys.dropWhileInPlace(_.name == key.name)
    foreignKeys += key
    key
  }

  def add(key: UniqueKey): UniqueKey = {
    key.table = this
    this.uniqueKeys.dropWhileInPlace(_.name == key.name)
    this.uniqueKeys += key
    key
  }

  def remove(column: Column): Unit = {
    columns.find(_.name == column.name) foreach { c =>
      columns -= c
      uniqueKeys --= uniqueKeys.filter { uk => uk.columns.size == 1 && uk.columns.contains(c.name) }
    }
  }

  def rename(column: Column, newName: Identifier): Unit = {
    remove(column)
    column.name = newName
    add(column)
  }

  def add(column: Column): Column = {
    val ukName = uniqueKeys.find { uk => uk.columns.size == 1 && uk.columns.contains(column.name) }.map(_.name.value)
    remove(column)
    columns += column
    if column.unique then this.createUniqueKey(ukName.getOrElse(this.name.value + "_" + column.name.value + "_key"), column.name.value)
    column
  }

  def add(cols: Column*): Unit = {
    cols foreach { col => add(col) }
  }

  def add(index: Index): Index = {
    index.table = this
    this.indexes.dropWhileInPlace(_.name == index.name)
    indexes += index
    index
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

  def getIndex(indexName: String): Option[Index] = {
    indexes.find(f => f.name.value == indexName)
  }

  def updateSchema(newSchema: Schema): Unit = {
    val oldSchema = this.schema
    this.schema = newSchema
    this.foreignKeys foreach { fk =>
      if (null != fk.referencedTable) {
        if (fk.referencedTable.schema == oldSchema) fk.referencedTable.schema = newSchema
      }
    }
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

  def convertIndexToUniqueKeys(): Unit = {
    val ui = indexes.filter(i => i.unique)
    indexes --= ui
    ui foreach { i => createUniqueKey(i.name.value, i.columns.map(_.value).toSeq: _*) }
  }
}

case class TableRef(var schema: Schema, var name: Identifier) extends Cloneable {

  def qualifiedName: String = {
    Table.qualify(schema, name)
  }

  def toCase(lower: Boolean): Unit = {
    this.name = this.name.toCase(lower)
  }
}
