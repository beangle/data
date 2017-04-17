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
package org.beangle.data.hibernate.udt

import java.io.{ Serializable => JSerializable }
import java.sql.{ PreparedStatement, ResultSet, Types }
import java.{ util => ju }

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.{ ParameterizedType, UserType }
import org.hibernate.engine.spi.SharedSessionContractImplementor

class EnumType extends UserType with ParameterizedType {

  var enum: Enumeration = null
  var ordinal: Boolean = true

  override def sqlTypes(): Array[Int] = {
    if (ordinal) Array(Types.INTEGER) else Array(Types.VARCHAR)
  }

  override def returnedClass: Class[_] = {
    enum.values.head.getClass
  }

  override def equals(x: Object, y: Object) = x == y

  override def hashCode(x: Object) = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, names: Array[String], session: SharedSessionContractImplementor, owner: Object): Object = {
    if (ordinal) {
      val value = resultSet.getInt(names(0))
      if (resultSet.wasNull()) null
      else enum(value)
    } else {
      val value = resultSet.getString(names(0))
      if (resultSet.wasNull()) null
      else enum.withName(value)
    }
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SharedSessionContractImplementor): Unit = {
    if (ordinal) {
      if (value == null) {
        statement.setNull(index, Types.INTEGER)
      } else {
        statement.setInt(index, value.asInstanceOf[Enumeration#Value].id)
      }
    } else {
      if (value == null) {
        statement.setNull(index, Types.VARCHAR)
      } else {
        statement.setString(index, value.toString)
      }
    }
  }

  override def setParameterValues(parameters: ju.Properties) {
    var enumClass = parameters.getProperty("enumClass")
    if (!enumClass.endsWith("$")) enumClass += "$"
    enum = Class.forName(enumClass).getDeclaredField("MODULE$").get(null).asInstanceOf[Enumeration]
  }

  override def deepCopy(value: Object): Object = value

  override def isMutable() = false

  override def disassemble(value: Object): JSerializable = {
    value.asInstanceOf[JSerializable]
  }
  override def assemble(cached: JSerializable, owner: Object): Object = {
    cached.asInstanceOf[Object]
  }

  override def replace(original: Object, target: Object, owner: Object) = original
}
