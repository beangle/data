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
class ForeignKey(table: Table, name: Name, column: Name = null) extends Constraint(table, name) {

  var cascadeDelete: Boolean = false
  var referencedColumns = new ListBuffer[Name]
  var referencedTable: TableRef = _

  addColumn(column)

  override def lowerCase(): Unit = {
    super.lowerCase()
    val lowers = referencedColumns.map { col => col.toLowerCase() }
    columns.clear()
    referencedColumns ++= lowers
  }

  override def attach(dialect: Dialect): Unit = {
    super.attach(dialect)
    val changed = referencedColumns.map { col => col.attach(dialect) }
    referencedColumns.clear()
    referencedColumns ++= changed
  }

  def alterSql: String = {
    require(null != name && null != table && null != referencedTable)
    require(!referencedColumns.isEmpty, " reference columns is empty.")
    require(!columns.isEmpty, "column's size should greate than 0")

    val dialect = table.dialect
    val referencedColumnNames = referencedColumns.map(x => x.qualified(table.dialect)).toList
    val result = "alter table " + table.qualifiedName + dialect.foreignKeySql(qualifiedName, columnNames,
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
    this.referencedTable = TableRef(table.dialect, table.name, table.schema)
    if (!cols.isEmpty) referencedColumns ++= cols
  }

  def refer(table: TableRef, cols: Name*): Unit = {
    this.referencedTable = table
    if (!cols.isEmpty) referencedColumns ++= cols
  }

  override def toString = "Foreign key(" + name + ')'
}
