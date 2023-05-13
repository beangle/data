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
