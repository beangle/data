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

import java.io.{ Serializable => JSerializable }
import java.sql.{ PreparedStatement, ResultSet, Types }
import java.time.{ LocalDate, YearMonth }

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType

class YearMonthType extends UserType {
  var returnedClass: Class[_] = classOf[YearMonth]
  var sqlTypes: Array[Int] = Array(Types.DATE)

  override def equals(x: Object, y: Object): Boolean = {
    x == y
  }

  override def hashCode(x: Object): Int = {
    x.hashCode()
  }

  override def nullSafeGet(resultSet: ResultSet, names: Array[String], session: SharedSessionContractImplementor, owner: Object): Object = {
    val value = resultSet.getDate(names(0))
    if (resultSet.wasNull()) {
      null
    } else {
      YearMonth.from(value.toLocalDate)
    }
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SharedSessionContractImplementor): Unit = {
    if (value == null) {
      statement.setNull(index, sqlTypes(0))
    } else {
      val ym = value.asInstanceOf[YearMonth]
      val date = LocalDate.of(ym.getYear, ym.getMonth, 1)
      statement.setDate(index, java.sql.Date.valueOf(date))
    }
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
