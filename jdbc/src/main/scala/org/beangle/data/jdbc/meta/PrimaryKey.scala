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

import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.dialect.Name

class PrimaryKey(table: Table, name: Name, column: Name) extends Constraint(table, name) {

  def this(table: Table, name: String, column: String) {
    this(table, Name(name), Name(column))
  }

  override def clone(): this.type = {
    super.clone()
  }

  addColumn(column)

  def addColumn(column: Column) {
    if (column != null) {
      addColumn(column.name)
      if (column.nullable) enabled = false
    }
  }

  def constraintSql = {
    val buf = new StringBuilder("primary key (")
    columns.foreach(col => (buf.append(col.qualified(table.dialect)).append(", ")))
    if (!columns.isEmpty) buf.delete(buf.size - 2, buf.size);
    buf.append(')').result
  }
}
