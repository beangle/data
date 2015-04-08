/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.jpa.hibernate.udt

import java.sql.{ PreparedStatement, ResultSet, Types }

import org.beangle.commons.lang.time.HourMinute
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.UserType
import java.io.{ Serializable => JSerializable }

class HourMinuteType extends UserType {

  override def sqlTypes() = Array(Types.SMALLINT)

  override def returnedClass = classOf[HourMinute]

  override def equals(x: Object, y: Object) = {
    x.asInstanceOf[HourMinute].value == y.asInstanceOf[HourMinute].value
  }

  override def hashCode(x: Object) = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, names: Array[String], session: SessionImplementor, owner: Object): Object = {
    val value = resultSet.getShort(names(0))
    if (resultSet.wasNull()) null
    else HourMinute(value)
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SessionImplementor): Unit = {
    if (value == null) {
      statement.setNull(index, Types.SMALLINT)
    } else {
      statement.setShort(index, value.asInstanceOf[HourMinute].value)
    }
  }

  override def deepCopy(value: Object): Object = value
  override def isMutable() = false
  override def disassemble(value: Object): JSerializable = {
    value.asInstanceOf[JSerializable]
  }
  override def assemble(cached: JSerializable, owner: Object): Object = cached.asInstanceOf[Object]
  override def replace(original: Object, target: Object, owner: Object) = original
}