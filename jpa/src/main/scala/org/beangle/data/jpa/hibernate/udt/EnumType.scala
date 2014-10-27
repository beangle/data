package org.beangle.data.jpa.hibernate.udt

import java.sql.{ PreparedStatement, ResultSet, Types }
import java.{ util => ju }

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.{ ParameterizedType, UserType }

class EnumType extends UserType with ParameterizedType {

  var enum: Enumeration = null
  var ordinal: Boolean = true

  override def sqlTypes(): Array[Int] = {
    if (ordinal) Array(Types.INTEGER) else Array(Types.VARCHAR)
  }

  override def returnedClass = enum.getClass()

  override def equals(x: Object, y: Object) = x == y

  override def hashCode(x: Object) = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, names: Array[String], session: SessionImplementor, owner: Object): Object = {
    if (ordinal) {
      val value = resultSet.getInt(names(0))
      if (resultSet.wasNull()) null
      else enum(value)
    } else {
      val value = resultSet.getString(names(0))
      if (resultSet.wasNull()) null
      else enum.withName(value)
    }
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SessionImplementor): Unit = {
    if (ordinal) {
      if (value == null) {
        statement.setNull(index, Types.INTEGER)
      } else {
        statement.setInt(index, value.asInstanceOf[Enumeration#Value].id)
      }
    } else {
      if (value == null) {
        statement.setNull(index, Types.VARCHAR)
      } else {
        statement.setString(index, value.toString)
      }
    }
  }

  override def setParameterValues(parameters: ju.Properties) {
    var enumClass = parameters.getProperty("enumClass")
    if (!enumClass.endsWith("$")) enumClass += "$"
    enum = Class.forName(enumClass).getDeclaredField("MODULE$").get(null).asInstanceOf[Enumeration]
  }

  override def deepCopy(value: Object): Object = value
  override def isMutable() = false
  override def disassemble(value: Object) = value.asInstanceOf[Serializable]
  override def assemble(cached: java.io.Serializable, owner: Object): Object = cached.asInstanceOf[Object]
  override def replace(original: Object, target: Object, owner: Object) = original
}