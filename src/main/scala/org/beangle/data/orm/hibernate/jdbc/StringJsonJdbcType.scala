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

package org.beangle.data.orm.hibernate.jdbc

import org.hibernate.`type`.SqlTypes
import org.hibernate.`type`.descriptor.java.JavaType
import org.hibernate.`type`.descriptor.jdbc.{JdbcLiteralFormatter, JdbcType}
import org.hibernate.`type`.descriptor.{ValueBinder, ValueExtractor}

import java.sql.Types.*

/** Store json as string in varchar/clob
 *
 * @param ddlTypeCode
 */
class StringJsonJdbcType(ddlTypeCode: Int = VARCHAR) extends JdbcType {

  assert(Set(VARCHAR, LONGVARCHAR, NVARCHAR, LONGNVARCHAR, CLOB, NCLOB).contains(ddlTypeCode))

  private val nationalized = ddlTypeCode == LONGNVARCHAR || ddlTypeCode == NCLOB

  override def getJdbcTypeCode: Int = VARCHAR

  override def getDefaultSqlTypeCode: Int = SqlTypes.JSON

  override def toString = "JsonJdbcType"

  override def getJdbcLiteralFormatter[T](javaType: JavaType[T]): JdbcLiteralFormatter[T] = {
    null
  }

  override def getBinder[X](javaType: JavaType[X]): ValueBinder[X] = {
    JsonAccessor.getStringBinder(javaType, nationalized)
  }

  override def getExtractor[X](javaType: JavaType[X]): ValueExtractor[X] = {
    JsonAccessor.getExtractor(javaType, nationalized)
  }
}
