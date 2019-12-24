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

import java.sql.Types._

import org.beangle.commons.lang.Strings

object SqlType {
  private val numberTypeCodes = Set(TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL)
  private val numberTypeNames = Set(
    "tinyint", "smallint", "int2", "integer",
    "int4", "int8", "bigint",
    "float4", "float", "double", "double precision",
    "numeric", "decimal", "number", "real",
    "identity"
  )

  private val stringTypeCodes = Set(CHAR, VARCHAR, LONGNVARCHAR)

  def isNumberType(code: Int): Boolean = {
    numberTypeCodes.contains(code)
  }

  def isStringType(code: Int): Boolean = {
    stringTypeCodes.contains(code)
  }

  def isNumberType(name: String): Boolean = {
    val typeClass =
      if (name.contains("(")) {
        Strings.substringBefore(name, "(")
      } else {
        name
      }
    numberTypeNames.contains(typeClass.toLowerCase)
  }

  def apply(code: Int, name: String): SqlType = {
    SqlType(code, name, None, None)
  }

  def apply(code: Int, name: String, precision: Int): SqlType = {
    apply(code, name, precision, 0)
  }

  def apply(code: Int, name: String, precision: Int, scale: Int): SqlType = {
    var scaleOption: Option[Int] = None
    if (scale > 0) scaleOption = Some(scale)
    if (SqlType.isNumberType(code)) {
      SqlType(code, name, Some(precision), scaleOption)
    } else {
      if (precision > 0) {
        SqlType(code, name, Some(precision), scaleOption)
      } else {
        SqlType(code, name, None, scaleOption)
      }
    }
  }
}

case class SqlType(code: Int, name: String, precision: Option[Int], scale: Option[Int]) {

  def isNumberType: Boolean = {
    SqlType.isNumberType(code)
  }

  def isStringType: Boolean = {
    SqlType.isStringType(code)
  }
}
