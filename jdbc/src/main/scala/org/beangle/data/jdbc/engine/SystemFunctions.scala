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

package org.beangle.data.jdbc.engine

import org.beangle.data.jdbc.meta.SqlType

class SystemFunctions {

  var currentDate: String = _

  var localTime: String = _
  var currentTime: String = _

  var localTimestamp: String = _
  var currentTimestamp: String = _

  def current(sqlType: SqlType): String = {
    sqlType.code match
      case java.sql.Types.TIMESTAMP_WITH_TIMEZONE => currentTimestamp
      case java.sql.Types.TIMESTAMP => localTimestamp
      case java.sql.Types.DATE => currentDate
      case java.sql.Types.TIME_WITH_TIMEZONE => currentTime
      case java.sql.Types.TIME => localTime
      case _ => null
  }
}
