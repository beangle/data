package org.beangle.data.orm.hibernate.udt

import org.beangle.commons.conversion.string.EnumConverters
import org.beangle.commons.lang.Enums
import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType}
import org.hibernate.`type`.descriptor.jdbc.{IntegerJdbcType, JdbcType, VarcharJdbcType}

class EnumType(`type`: Class[_]) extends AbstractClassJavaType[Object](`type`) {

  var ordinal: Boolean = true
  private val converter = EnumConverters.getConverter(`type`).get

  override def unwrap[X](value: Object, valueType: Class[X], options: WrapperOptions): X = {
    if (value eq null) null.asInstanceOf[X]
    else {
      if value.getClass == valueType then value.asInstanceOf[X]
      else {
        if (valueType == classOf[Integer]) then Enums.id(value).asInstanceOf[X]
        else value.toString.asInstanceOf[X]
      }
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): AnyRef = {
    value match
      case null => null
      case _ => converter.apply(value.toString)
  }

  def toJdbcType(): JdbcType = {
    if ordinal then IntegerJdbcType.INSTANCE else VarcharJdbcType.INSTANCE
  }

  override def isWider(javaType: JavaType[_]): Boolean = {
    val jtc = javaType.getJavaTypeClass
    if ordinal then jtc == classOf[Integer] else jtc == classOf[String]
  }
}
