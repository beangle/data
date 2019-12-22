package org.beangle.data.transfer.io

import org.beangle.commons.lang.{Numbers, Primitives}

object DataType extends Enumeration(1) {
  val String, Boolean, Short, Integer, Long, Float, Double, Date, Time, DateTime, YearMonth, MonthDay = Value

  def toType(clazz: Class[_]): Value = {
    val clz = Primitives.wrap(clazz)
    if (classOf[java.lang.Boolean].isAssignableFrom(clz)) {
      Boolean
    } else if (classOf[java.lang.Short].isAssignableFrom(clz)) {
      Short
    } else if (classOf[java.lang.Integer].isAssignableFrom(clz)) {
      Integer
    } else if (classOf[java.lang.Long].isAssignableFrom(clz)) {
      Long
    } else if (classOf[java.lang.Float].isAssignableFrom(clz)) {
      Float
    } else if (classOf[java.lang.Double].isAssignableFrom(clz)) {
      Double
    } else if (classOf[java.sql.Date].isAssignableFrom(clz) || classOf[java.time.LocalDate].isAssignableFrom(clz)) {
      Date
    } else if (classOf[java.sql.Time].isAssignableFrom(clz) || classOf[java.time.LocalTime].isAssignableFrom(clz)) {
      Time
    } else if (classOf[java.sql.Timestamp].isAssignableFrom(clz) || classOf[java.util.Date].isAssignableFrom(clz)
      || classOf[java.time.LocalDateTime].isAssignableFrom(clz)) {
      DateTime
    } else if (classOf[java.time.YearMonth].isAssignableFrom(clz)) {
      YearMonth
    } else if (classOf[java.time.MonthDay].isAssignableFrom(clz)) {
      MonthDay
    } else {
      String
    }
  }

  def convert(str: String, dataType: DataType.Value): Any = {
    if (null == str) {
      null
    }
    else {
      dataType match {
        case String => str
        case Short => Numbers.convert2Short(str)
        case Integer => Numbers.convert2Int(str)
        case Long => Numbers.convert2Long(str)
        case Float => Numbers.convert2Float(str)
        case Double => Numbers.convert2Double(str)
        case Date =>
        case DateTime =>
        case Time =>
        case YearMonth =>
        case MonthDay =>
      }
    }
  }

}