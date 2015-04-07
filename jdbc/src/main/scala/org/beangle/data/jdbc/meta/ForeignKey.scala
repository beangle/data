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

import scala.collection.mutable.ListBuffer
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.dialect.Name

/**
 * JDBC foreign key metadata
 *
 * @author chaostone
 */
class ForeignKey(t: Table, n: Name, column: Name = null) extends Constraint(t, n) {

  var cascadeDelete: Boolean = false
  var referencedColumns = new ListBuffer[Name]
  var referencedTable: TableRef = _

  addColumn(column)

  override def toLowerCase(): Unit = {
    super.toLowerCase()
    val lowers = referencedColumns.map { col => col.toLowerCase() }
    referencedColumns.clear()
    referencedColumns ++= lowers

    if (this.table.schema.value == referencedTable.schema.value.toLowerCase()) {
      referencedTable.toLowerCase()
    }
  }

  override def attach(dialect: Dialect): Unit = {
    super.attach(dialect)
    val changed = referencedColumns.map { col => col.attach(dialect) }
    referencedColumns.clear()
    referencedColumns ++= changed

    if (this.table.schema == referencedTable.schema) {
      referencedTable.name = referencedTable.name.attach(dialect)
    }
  }

  def alterSql: String = {
    require(null != this.name && null != table && null != referencedTable)
    require(!referencedColumns.isEmpty, " reference columns is empty.")
    require(!columns.isEmpty, s"${this.name} column's size should greate than 0")

    val dialect = table.dialect
    val referencedColumnNames = referencedColumns.map(x => x.qualified(table.dialect)).toList
    val result = "alter table " + this.table.qualifiedName + dialect.foreignKeySql(qualifiedName, columnNames,
      referencedTable.qualifiedName, referencedColumnNames)

    if (cascadeDelete && dialect.supportsCascadeDelete) result + " on delete cascade" else result
  }

  override def clone(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.cascadeDelete = this.cascadeDelete
    cloned.referencedTable = this.referencedTable
    var newColumns = new ListBuffer[Name]
    newColumns ++= referencedColumns
    cloned.referencedColumns = newColumns
    cloned
  }

  def refer(table: Table, cols: Name*): Unit = {
    this.referencedTable = TableRef(table.dialect, table.schema, table.name)
    if (!cols.isEmpty) referencedColumns ++= cols
  }

  def refer(table: TableRef, cols: Name*): Unit = {
    this.referencedTable = table
    if (!cols.isEmpty) referencedColumns ++= cols
  }

  override def toString = "Foreign key(" + name + ')'
}
