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

package org.beangle.data.hibernate.udt

import org.beangle.commons.lang.math.{Decimal5, TinyDecimal5}
import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.AbstractClassJavaType
import org.hibernate.`type`.descriptor.jdbc.JdbcType
import org.hibernate.dialect.Dialect

import java.math.BigDecimal as JBigDecimal

class Decimal5Type extends AbstractClassJavaType[Decimal5](classOf[Decimal5]) {

  override def unwrap[X](value: Decimal5, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if valueType == classOf[Decimal5] then value.asInstanceOf[X]
      else if valueType == classOf[JBigDecimal] then value.toBigDecimal.asInstanceOf[X]
      else throw unknownUnwrap(valueType)
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): Decimal5 = {
    value match {
      case null => null
      case d: Decimal5 => d
      case b: JBigDecimal => Decimal5.of(b)
      case s: String => Decimal5.of(s)
      case _ => throw new RuntimeException(s"Cannot support convert from ${value.getClass} to Decimal5")
    }
  }

  override def getDefaultSqlPrecision(dialect: Dialect, jdbcType: JdbcType): Int = 19

  override def getDefaultSqlScale(dialect: Dialect, jdbcType: JdbcType): Int = 5
}

class TinyDecimal5Type extends AbstractClassJavaType[TinyDecimal5](classOf[TinyDecimal5]) {

  override def unwrap[X](value: TinyDecimal5, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if valueType == classOf[TinyDecimal5] then value.asInstanceOf[X]
      else if valueType == classOf[JBigDecimal] then value.toBigDecimal.asInstanceOf[X]
      else throw unknownUnwrap(valueType)
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): TinyDecimal5 = {
    value match {
      case null => null
      case d: TinyDecimal5 => d
      case b: JBigDecimal => TinyDecimal5.of(b)
      case s: String => TinyDecimal5.of(s)
      case _ => throw new RuntimeException(s"Cannot support convert from ${value.getClass} to TinyDecimal5")
    }
  }

  override def getDefaultSqlPrecision(dialect: Dialect, jdbcType: JdbcType): Int = 10

  override def getDefaultSqlScale(dialect: Dialect, jdbcType: JdbcType): Int = 5
}
