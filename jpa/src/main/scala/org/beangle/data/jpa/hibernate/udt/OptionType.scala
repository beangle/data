package org.beangle.data.jpa.hibernate.udt

import java.io.{ Serializable => JSerializable }
import java.sql.{ PreparedStatement, ResultSet }

import org.beangle.commons.lang.{ JDouble, JFloat, JLong, JByte, JChar, JInt, JBoolean }
import org.hibernate.`type`.AbstractSingleColumnStandardBasicType
import org.hibernate.`type`.StandardBasicTypes.{ BYTE, CHARACTER, DOUBLE, FLOAT, INTEGER, LONG, BOOLEAN }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.UserType

object OptionBasicType {
  val java2HibernateTypes: Map[Class[_], AbstractSingleColumnStandardBasicType[_]] =
    Map((classOf[JChar], CHARACTER),
      (classOf[JByte], BYTE),
      (classOf[JBoolean], BOOLEAN),
      (classOf[JInt], INTEGER),
      (classOf[JLong], LONG),
      (classOf[JFloat], FLOAT),
      (classOf[JDouble], DOUBLE))
}

abstract class OptionBasicType[T](clazz: Class[T]) extends UserType {
  import OptionBasicType._

  def inner: AbstractSingleColumnStandardBasicType[_] = java2HibernateTypes(clazz)

  def sqlTypes = Array(inner.sqlType)

  def returnedClass = classOf[Option[T]]

  final def nullSafeGet(rs: ResultSet, names: Array[String], session: SessionImplementor, owner: Object) = {
    val x = inner.nullSafeGet(rs, names, session, owner)
    if (x == null) None else Some(x)
  }

  final def nullSafeSet(ps: PreparedStatement, value: Object, index: Int, session: SessionImplementor) = {
    inner.nullSafeSet(ps, value.asInstanceOf[Option[_]].getOrElse(null), index, session)
  }

  def isMutable = false

  def equals(x: Object, y: Object) = x.equals(y)

  def hashCode(x: Object) = x.hashCode

  def deepCopy(value: Object) = value

  def replace(original: Object, target: Object, owner: Object) = original

  def disassemble(value: Object) = value.asInstanceOf[JSerializable]

  def assemble(cached: JSerializable, owner: Object): Object = cached.asInstanceOf[Object]
}

class OptionCharType extends OptionBasicType(classOf[JChar])

class OptionByteType extends OptionBasicType(classOf[JByte])

class OptionIntType extends OptionBasicType(classOf[JInt])

class OptionBooleanType extends OptionBasicType(classOf[JBoolean])

class OptionLongType extends OptionBasicType(classOf[JLong])

class OptionFloatType extends OptionBasicType(classOf[JFloat])

class OptionDoubleType extends OptionBasicType(classOf[JDouble])

