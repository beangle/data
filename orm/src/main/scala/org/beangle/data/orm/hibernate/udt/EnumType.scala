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

import org.beangle.commons.conversion.Converter
import org.beangle.commons.conversion.string.EnumConverters
import org.beangle.commons.lang.Enums
import org.hibernate.engine.spi.{SessionImplementor, SharedSessionContractImplementor}
import org.hibernate.usertype.{ParameterizedType, UserType}

import java.io.Serializable as JSerializable
import java.sql.{PreparedStatement, ResultSet, Types}
import java.util as ju

class EnumType extends UserType[Object] with ParameterizedType {

  private var converter: Converter[String, Object] = _

  var returnedClass: Class[Object] = _

  var ordinal: Boolean = true

  override def getSqlType: Int = {
    if (ordinal) Types.INTEGER else Types.VARCHAR
  }

  override def equals(x: Object, y: Object) = x == y

  override def hashCode(x: Object) = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: AnyRef): Object = {
    if (ordinal) {
      val value = resultSet.getInt(position)
      if resultSet.wasNull() then null else converter.apply(value.toString)
    } else {
      val value = resultSet.getString(position)
      if resultSet.wasNull() then null else converter.apply(value)
    }
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SharedSessionContractImplementor): Unit = {
    if (ordinal) {
      if (value == null) {
        statement.setNull(index, Types.INTEGER)
      } else {
        statement.setInt(index, Enums.id(value))
      }
    } else {
      if (value == null) {
        statement.setNull(index, Types.VARCHAR)
      } else {
        statement.setString(index, value.toString)
      }
    }
  }

  override def setParameterValues(parameters: ju.Properties): Unit = {
    val enumClass = parameters.getProperty("enumClass")
    returnedClass = Class.forName(enumClass).asInstanceOf[Class[Object]]
    converter = EnumConverters.getConverter(returnedClass).get
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
