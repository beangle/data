package org.beangle.data.orm.hibernate.udt

import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType}
import org.hibernate.`type`.descriptor.jdbc.*

import java.lang.reflect.{Constructor, Field}

class ValueType(`type`: Class[_]) extends AbstractClassJavaType[Object](`type`) {

  var valueClass: Class[_] = _
  private var valueField: Field = _
  private var constructor: Constructor[_] = _

  this.`type`.getDeclaredFields foreach { f =>
    valueClass = f.getType
    valueField = f
    valueField.setAccessible(true)
    constructor = `type`.getConstructor(valueClass)
  }

  if (this.valueField == null || valueClass == null)
    throw new RuntimeException(s"Cannot find field for ${this.`type`}")

  override def unwrap[X](value: Object, valueType: Class[X], options: WrapperOptions): X = {
    if value eq null then null.asInstanceOf[X]
    else {
      if value.getClass == valueType then value.asInstanceOf[X]
      else valueField.get(value).asInstanceOf[X]
    }
  }

  override def wrap[X](value: X, options: WrapperOptions): AnyRef = {
    value match
      case null => null
      case _ => constructor.newInstance(value)
  }

  def toJdbcType(): JdbcType = {
    if valueClass == classOf[Long] then BigIntJdbcType.INSTANCE
    else if valueClass == classOf[Int] then IntegerJdbcType.INSTANCE
    else if valueClass == classOf[Short] then SmallIntJdbcType.INSTANCE
    else if valueClass == classOf[Boolean] then BooleanJdbcType.INSTANCE
    else if valueClass == classOf[Float] then FloatJdbcType.INSTANCE
    else if valueClass == classOf[Double] then DoubleJdbcType.INSTANCE
    else VarcharJdbcType.INSTANCE
  }

  override def isWider(javaType: JavaType[_]): Boolean = {
    val jtc = javaType.getJavaTypeClass
    jtc == classOf[Integer] || jtc == classOf[java.lang.Long]
  }
}
