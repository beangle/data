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

import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType}
import org.hibernate.`type`.descriptor.jdbc.{DateJdbcType, JdbcType}

import java.time.{LocalDate, YearMonth}

class YearMonthType extends AbstractClassJavaType[YearMonth](classOf[YearMonth]) {

  override def unwrap[X](value: YearMonth, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if valueType == classOf[YearMonth] then
        value.asInstanceOf[X]
      else if valueType == classOf[LocalDate] then
        value.atDay(1).asInstanceOf[X]
      else if valueType == classOf[java.sql.Date] then
        java.sql.Date.valueOf(value.atDay(1)).asInstanceOf[X]
      else
        throw unknownUnwrap(valueType);
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): YearMonth = {
    value match {
      case null => null
      case ym: YearMonth => ym
      case s: String => YearMonth.parse(s)
      case d: java.sql.Date => YearMonth.from(d.toLocalDate)
      case _ => throw new RuntimeException(s"Cannot support convert from ${value.getClass} to yearMonth")
    }
  }

  def toJdbcType(): JdbcType = DateJdbcType.INSTANCE

  override def isWider(javaType: JavaType[_]): Boolean = {
    javaType.getJavaType.getTypeName == "java.sql.Date"
  }
}
