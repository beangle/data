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

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType

import java.io.Serializable as JSerializable
import java.sql.{PreparedStatement, ResultSet, Types}
import java.time.{LocalDate, YearMonth}

class YearMonthType extends UserType[YearMonth] {
  var returnedClass = classOf[YearMonth]

  override def getSqlType: Int = Types.DATE

  override def equals(x: YearMonth, y: YearMonth): Boolean = x == y

  override def hashCode(x: YearMonth): Int = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Object): YearMonth = {
    val value = resultSet.getDate(position)
    if resultSet.wasNull() then null else YearMonth.from(value.toLocalDate)
  }

  override def nullSafeSet(statement: PreparedStatement, value: YearMonth, index: Int, session: SharedSessionContractImplementor): Unit = {
    if (value == null) {
      statement.setNull(index, getSqlType)
    } else {
      val date = LocalDate.of(value.getYear, value.getMonth, 1)
      statement.setDate(index, java.sql.Date.valueOf(date))
    }
  }

  override def deepCopy(value: YearMonth): YearMonth = value

  override def isMutable() = false

  override def disassemble(value: YearMonth): JSerializable = {
    value.asInstanceOf[JSerializable]
  }

  override def assemble(cached: JSerializable, owner: Object): YearMonth = {
    cached.asInstanceOf[YearMonth]
  }

  override def replace(original: YearMonth, target: YearMonth, owner: Object): YearMonth = original
}
