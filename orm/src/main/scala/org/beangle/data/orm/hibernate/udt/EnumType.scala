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

package org.beangle.data.orm.hibernate.udt

import org.beangle.commons.conversion.string.EnumConverters
import org.beangle.commons.lang.Enums
import org.beangle.data.orm.hibernate.jdbc.IntJdbcType
import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType}
import org.hibernate.`type`.descriptor.jdbc.{IntegerJdbcType, JdbcType, VarcharJdbcType}

class EnumType(`type`: Class[_]) extends AbstractClassJavaType[Object](`type`) {

  var ordinal: Boolean = true
  private val converter = EnumConverters.getConverter(`type`).get

  override def unwrap[X](value: Object, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if value.getClass == valueType then value.asInstanceOf[X]
      else {
        if (valueType == classOf[Integer] ||valueType == classOf[Int]) then Enums.id(value).asInstanceOf[X]
        else value.toString.asInstanceOf[X]
      }
    }
  }


  /** wrap id/name/ordinal to Enum
   *
   * @param value cannot be null or not existed id/ordinal
   * @param options
   * @tparam X
   * @return
   */
  override def wrap[X](value: X, options: WrapperOptions): AnyRef = {
    converter.apply(value.toString)
  }

  def toJdbcType(): JdbcType = {
    if ordinal then IntJdbcType else VarcharJdbcType.INSTANCE
  }

  override def isWider(javaType: JavaType[_]): Boolean = {
    val jtc = javaType.getJavaTypeClass
    if ordinal then (jtc == classOf[Integer] || jtc == classOf[Int]) else jtc == classOf[String]
  }
}
