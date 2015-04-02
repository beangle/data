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

/**
 * Table Constraint Metadata
 *
 * @author chaostone
 */
class Constraint(var name: String) extends Ordered[Constraint] with Cloneable {

  var columns = new ListBuffer[String]

  var enabled: Boolean = true

  var table: Table = null

  def lowerCase(): Unit = {
    if (null != name) this.name = name.toLowerCase
    val lowers = columns.map { col => col.toLowerCase() }
    columns.clear()
    columns ++= lowers
  }

  def addColumn(column: String): Unit = {
    if (column != null) columns += column
  }

  override def compare(o: Constraint): Int = {
    if (null == name) 0 else name.compareTo(o.name)
  }

  override def clone(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    var newColumns = new ListBuffer[String]
    newColumns ++= columns
    cloned.columns = newColumns
    cloned
  }

}
