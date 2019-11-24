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

import org.beangle.commons.lang.Strings

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
}

class Table(var schema: Schema, var name: Identifier) extends Ordered[Table] with Cloneable with Comment {

  var primaryKey: Option[PrimaryKey] = None
  val columns = new ListBuffer[Column]
  val uniqueKeys = new ListBuffer[UniqueKey]
  val foreignKeys = new ListBuffer[ForeignKey]
  val indexes = new ListBuffer[Index]

  def engine: Engine = {
    schema.database.engine
  }

  def quotedColumnNames: List[String] = {
    val e = engine
    columns.result.map(_.name.toLiteral(e))
  }

  def qualifiedName: String = {
    Table.qualify(schema, name)
  }

  def attach(engine: Engine): this.type = {
    columns foreach { col =>
      val st = col.sqlType
      col.sqlType = engine.toType(st.code, st.length.getOrElse(0), st.precision.getOrElse(0), st.scale.getOrElse(0))
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
    columns.find(f => f.name.value == columnName).get
  }

  def getColumn(columnName: String): Option[Column] = {
    columns.find(f => f.name.value == columnName)
  }

  def getForeignKey(keyName: String): Option[ForeignKey] = {
    foreignKeys.find(f => f.name.value == keyName)
  }

  def createPrimaryKey(columnNames: Identifier*): PrimaryKey = {
    val pk = if (columnNames.size == 1) {
      new PrimaryKey(this, null.asInstanceOf[Identifier], columnNames.head)
    } else {
      val pk2 = new PrimaryKey(this, null.asInstanceOf[Identifier], null.asInstanceOf[Identifier])
      columnNames.foreach { cn =>
        this.columns foreach (c => if (c.name == cn) pk2.addColumn(c))
      }
      pk2
    }
    this.primaryKey = Some(pk)
    pk
  }

  def createForeignKey(columnName: Identifier, refTable: Table): ForeignKey = {
    refTable.primaryKey match {
      case Some(pk) =>
        val fk = new ForeignKey(this, Identifier("fk_temp"), columnName)
        fk.refer(refTable, pk.columns.head)
        fk.name = Identifier(Constraint.autoname(fk))
        this.add(fk)
      case None =>
        throw new RuntimeException("Cannot refer on a table without primary key")
    }
  }

  def add(key: ForeignKey): ForeignKey = {
    key.table = this
    foreignKeys += key
    key
  }

  def add(key: UniqueKey): UniqueKey = {
    key.table = this
    this.uniqueKeys += key
    key
  }

  def add(column: Column): Column = {
    columns.find (_.name == column.name) foreach columns.subtractOne
    columns += column
    column
  }

  def add(index: Index): Index = {
    index.table = this
    indexes += index
    index
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
}

case class TableRef(var schema: Schema, var name: Identifier) extends Cloneable {

  def qualifiedName: String = {
    Table.qualify(schema, name)
  }

  def toCase(lower: Boolean): Unit = {
    this.name = this.name.toCase(lower)
  }
}
