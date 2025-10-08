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

import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.{AbstractClassJavaType, JavaType}
import org.hibernate.`type`.descriptor.jdbc.*

import java.lang.reflect.{Constructor, Field}

object ValueType {

  def getJdbcType(clazz: Class[_]): JdbcType = {
    if clazz == classOf[Long] then BigIntJdbcType.INSTANCE
    else if clazz == classOf[Int] then IntegerJdbcType.INSTANCE
    else if clazz == classOf[Short] then SmallIntJdbcType.INSTANCE
    else if clazz == classOf[Boolean] then BooleanJdbcType.INSTANCE
    else if clazz == classOf[Float] then FloatJdbcType.INSTANCE
    else if clazz == classOf[Double] then DoubleJdbcType.INSTANCE
    else VarcharJdbcType.INSTANCE
  }
}

class ValueType[T](`type`: Class[T]) extends AbstractClassJavaType[T](`type`) {

  private var valueClass: Class[_] = _
  private var valueField: Field = _
  private var constructor: Constructor[T] = _

  this.`type`.getDeclaredFields foreach { f =>
    valueClass = f.getType
    valueField = f
    valueField.setAccessible(true)
    constructor = `type`.getConstructor(valueClass)
  }

  if (this.valueField == null || valueClass == null)
    throw new RuntimeException(s"Cannot find field for ${this.`type`}")

  override def unwrap[X](v: T, valueType: Class[X], options: WrapperOptions): X = {
    v match
      case null => null.asInstanceOf[X]
      case _ => if v.getClass == valueType then v.asInstanceOf[X] else valueField.get(v).asInstanceOf[X]
  }

  override def wrap[X](value: X, options: WrapperOptions): T = {
    value match
      case null => null.asInstanceOf[T]
      case _ => if `type` == value.getClass then value.asInstanceOf[T] else constructor.newInstance(value)
  }

  override def isWider(javaType: JavaType[_]): Boolean = {
    val jtc = javaType.getJavaTypeClass
    jtc == classOf[Integer] || jtc == classOf[java.lang.Long]
  }

  def getValueClass: Class[_] = valueClass
}
