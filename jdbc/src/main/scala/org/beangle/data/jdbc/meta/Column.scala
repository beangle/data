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

import java.sql.Types

import org.beangle.data.jdbc.dialect.Dialect
/**
 * DBC column metadata
 *
 * @author chaostone
 */
class Column(var name: String, var typeCode: Int) extends Ordered[Column] with Cloneable {
  var typeName: String = null
  // charactor length or numeric precision
  var size: Int = _
  var scale: Short = _
  var nullable: Boolean = _
  var defaultValue: String = null
  var unique: Boolean = _
  var comment: String = null
  var checkConstraint: String = null
  var position: Int = _

  override def clone = super.clone().asInstanceOf[Column]

  def lowerCase() {
    this.name = name.toLowerCase
  }

  def hasCheckConstraint = checkConstraint != null

  def getSqlType(dialect: Dialect) = {
    if (typeCode == Types.OTHER) typeName else
      try {
        dialect.typeNames.get(typeCode, size, size, scale)
      } catch {
        case e: Exception => println("Cannot find type code"+typeCode); typeName
      }
  }

  override def toString = "Column(" + name + ')'

  override def compare(other: Column) = position - other.position
}
