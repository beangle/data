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
/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
import org.beangle.commons.collection.Collections
import org.beangle.data.jdbc.dialect.Name
/**
 * JDBC index metadata
 *
 * @author chaostone
 */
class Index(var name: Name, var table: Table) extends Cloneable {

  var columns = Collections.newBuffer[Name]

  var unique: Boolean = false

  var ascOrDesc: Option[Boolean] = None

  def toLowerCase(): Unit = {
    this.name = name.toLowerCase()
    val lowers = columns.map { col => col.toLowerCase() }
    columns.clear()
    columns ++= lowers
  }

  def attach(dialect: Dialect): Unit = {
    name = name.attach(dialect)
    val changed = columns.map { col => col.attach(dialect) }
    columns.clear()
    columns ++= changed
  }

  def addColumn(column: Name): Unit = {
    if (column != null) columns += column
  }

  override def toString: String = {
    "IndexMatadata(" + qualifiedName + ')'
  }

  override def clone(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    val newColumns = Collections.newBuffer[Name]
    newColumns ++= columns
    cloned.columns = newColumns
    cloned
  }

  def createSql: String = {
    val buf = new StringBuilder("create")
      .append(if (unique) " unique" else "")
      .append(" index ")
      .append(qualifiedName)
      .append(" on ")
      .append(table.qualifiedName)
      .append(" (");
    val iter = columns.iterator
    while (iter.hasNext) {
      buf.append(iter.next)
      if (iter.hasNext) buf.append(", ")
    }
    buf.append(")")
    buf.toString()
  }

  def qualifiedName: String = {
    name.qualified(table.dialect)
  }
  def dropSql: String = {
    "drop index " + table.qualifiedName + "." + qualifiedName
  }

}
