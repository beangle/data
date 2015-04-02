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
/**
 * JDBC index metadata
 *
 * @author chaostone
 */
class Index(var name: String, var table: Table) extends Cloneable {

  val columns = new ListBuffer[String]

  var unique: Boolean = false

  var ascOrDesc: Option[Boolean] = None

  def lowerCase(): Unit = {
    this.name = name.toLowerCase()
    val lowers = columns.map { col => col.toLowerCase() }
    columns.clear()
    columns ++= lowers
  }

  def addColumn(column: String): Unit = {
    if (column != null) columns += column
  }

  override def toString: String = {
    "IndexMatadata(" + name + ')'
  }

  override def clone(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    cloned.columns.clear()
    cloned.columns ++= columns
    cloned
  }

  def createSql(dialect: Dialect): String = {
    val buf = new StringBuilder("create")
      .append(if (unique) " unique" else "")
      .append(" index ")
      .append(name)
      .append(" on ")
      .append(table.identifier)
      .append(" (");
    val iter = columns.iterator
    while (iter.hasNext) {
      buf.append(iter.next)
      if (iter.hasNext) buf.append(", ")
    }
    buf.append(")")
    buf.toString()
  }

  def dropSql(dialect: Dialect): String = {
    "drop index " + table.identifier + "." + name
  }

}
