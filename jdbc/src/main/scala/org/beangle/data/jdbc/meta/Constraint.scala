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

import org.beangle.data.jdbc.engine.Engine

import scala.collection.mutable.ListBuffer

/**
 * Table Constraint Metadata
 * @author chaostone
 */
class Constraint(var table: Table, var name: Identifier) extends Ordered[Constraint] with Cloneable {

  var columns = new ListBuffer[Identifier]

  var enabled: Boolean = true

  def literalName: String = {
    name.toLiteral(table.schema.database.engine)
  }

  def attach(engine: Engine): Unit = {
    name = name.attach(engine)
    val changed = columns.map { col => col.attach(engine) }
    columns.clear()
    columns ++= changed
  }

  def toCase(lower: Boolean): Unit = {
    if (null != name) this.name = name.toCase(lower)
    val lowers = columns.map { col => col.toCase(lower) }
    columns.clear()
    columns ++= lowers
  }

  def columnNames: List[String] = {
    columns.map(x => x.toLiteral(table.schema.database.engine)).toList
  }

  def addColumn(column: Identifier): Unit = {
    if (column != null) columns += column
  }

  override def compare(o: Constraint): Int = {
    if (null == name) 0 else name.compare(o.name)
  }

  protected def sameNameColumns(columns1: Iterable[Identifier],columns2: Iterable[Identifier]):Boolean={
    columns1.toSet == columns2.toSet
  }

  override def clone(): this.type = {
    val cloned = super.clone().asInstanceOf[this.type]
    var newColumns = new ListBuffer[Identifier]
    newColumns ++= columns
    cloned.columns = newColumns
    cloned
  }

}

import java.math.BigInteger
import java.security.MessageDigest

object Constraint {

  def autoname(fk: ForeignKey): String = {
    val sb = new StringBuilder()
      .append("table`").append(fk.table.name).append("`")
      .append("references`").append(fk.referencedTable.name).append("`")
    fk.columns foreach { fc =>
      sb.append("column`").append(fc.value).append("`");
    }
    "fk_" + hashedName(sb.toString)
  }

  def autoname(uk: Constraint): String = {
    uk match {
      case _: PrimaryKey => "pk_" + hashedName(uk.table.name.value)
      case uk: UniqueKey =>
        val sb = new StringBuilder()
          .append("table`").append(uk.table.name).append("`")
        uk.columns foreach { fc =>
          sb.append("column`").append(fc.value).append("`");
        }
        "uk_" + hashedName(sb.toString)
      case _ => ""
    }
  }

  def autoname(idx: Index): String = {
    val sb = new StringBuilder()
      .append("table`").append(idx.table.name).append("`")
    idx.columns foreach { fc =>
      sb.append("column`").append(fc.value).append("`");
    }
    "idx_" + hashedName(sb.toString)
  }

  /** hash string to full alphanumeric,length less than 30
   * @param s
   * @return
   */
  def hashedName(s: String): String = {
    val md = MessageDigest.getInstance("MD5")
    md.reset()
    md.update(s.getBytes)
    val digest = md.digest() // 16bytes,128bits
    val radix=35 // 0~9 a~y 6bits,128/5=25,result length isn't greate than 25
    new BigInteger(1, digest).toString(radix)
  }
}
