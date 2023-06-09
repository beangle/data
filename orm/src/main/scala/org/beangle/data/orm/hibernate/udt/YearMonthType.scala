package org.beangle.data.orm.hibernate.udt

import org.beangle.commons.conversion.string.EnumConverters
import org.beangle.commons.lang.Enums
import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType, LocalDateJavaType}
import org.hibernate.`type`.descriptor.jdbc.{DateJdbcType, IntegerJdbcType, JdbcType, VarcharJdbcType}

import java.time.{LocalDate, YearMonth}

class YearMonthType extends AbstractClassJavaType[YearMonth](classOf[YearMonth]) {

  override def unwrap[X](value: YearMonth, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if valueType == classOf[YearMonth] then
        value.asInstanceOf[X]
      else if valueType == classOf[LocalDate] then
        value.atDay(1).asInstanceOf[X]
      else if valueType == classOf[java.sql.Date] then
        java.sql.Date.valueOf(value.atDay(1)).asInstanceOf[X]
      else
        throw unknownUnwrap(valueType);
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): YearMonth = {
    value match {
      case null => null
      case s: String => YearMonth.parse(s)
      case d: java.sql.Date => YearMonth.from(d.toLocalDate)
      case _ => throw new RuntimeException(s"Cannot support convert from ${value.getClass} to yearMonth")
    }
  }

  def toJdbcType(): JdbcType = DateJdbcType.INSTANCE

  override def isWider(javaType: JavaType[_]): Boolean = {
    javaType.getJavaType.getTypeName == "java.sql.Date"
  }
}
