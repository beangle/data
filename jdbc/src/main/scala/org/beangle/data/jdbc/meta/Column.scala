/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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

import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.dialect.Name
/**
 * DBC column metadata
 *
 * @author chaostone
 */
class Column(var name: Name, var typeCode: Int) extends Ordered[Column] with Cloneable {

  var typeName: String = null
  /**
   *  Charactor length or numeric precision
   *  The number 123.45 has a precision of 5 and a scale of 2
   */
  var size: Int = _
  var scale: Short = _
  var nullable: Boolean = _
  var defaultValue: String = null
  var unique: Boolean = _
  var comment: String = null
  var checkConstraint: String = null
  var position: Int = _

  def this(name: String, typeCode: Int) {
    this(Name(name), typeCode)
  }
  override def clone(): Column = {
    super.clone().asInstanceOf[Column]
    //    val tu = dialect.translate(typeCode, size, scale)
    //    col.typeCode = tu._1
    //    if (null != tu._2) col.typeName = tu._2
    //    col
  }

  def toCase(lower: Boolean): Unit = {
    this.name = name.toCase(lower)
  }

  def qualifiedName(dialect: Dialect): String = {
    name.qualified(dialect)
  }

  def hasCheckConstraint = checkConstraint != null

  override def toString = "Column(" + name + ')'

  override def compare(other: Column) = position - other.position
}
