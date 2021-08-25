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

import org.beangle.commons.collection.Collections
import org.beangle.data.jdbc.engine.Engine

/**
  * JDBC index metadata
  *
  * @author chaostone
  */
class Index(var table: Table, var name: Identifier) extends Cloneable {

  var columns: collection.mutable.Buffer[Identifier] = Collections.newBuffer

  var unique: Boolean = false

  var ascOrDesc: Option[Boolean] = None

  def toCase(lower: Boolean): Unit = {
    this.name = name.toCase(lower)
    val lowers = columns.map { col => col.toCase(lower) }
    columns.clear()
    columns ++= lowers
  }

  def attach(engine: Engine): Unit = {
    name = name.attach(engine)
    val changed = columns.map { col => col.attach(engine) }
    columns.clear()
    columns ++= changed
  }

  def addColumn(column: Identifier): Unit = {
    if (column != null) columns += column
  }

  override def toString: String = {
    "Index(" + literalName + ')'
  }

  override def clone(): Index = {
    val cloned = super.clone().asInstanceOf[Index]
    val newColumns = Collections.newBuffer[Identifier]
    newColumns ++= columns
    cloned.columns = newColumns
    cloned
  }

  def literalName: String = {
    name.toLiteral(table.schema.database.engine)
  }

  override def equals(other: Any): Boolean = {
    other match {
      case c: Index =>
        this.name == c.name && this.columns == c.columns && this.unique == c.unique && this.ascOrDesc == c.ascOrDesc
      case _ => false
    }
  }

}
