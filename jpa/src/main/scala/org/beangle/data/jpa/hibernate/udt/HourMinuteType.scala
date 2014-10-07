package org.beangle.data.jpa.hibernate.udt

import java.sql.{ PreparedStatement, ResultSet, Types }

import org.beangle.commons.lang.time.HourMinute
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.UserType

class HourMinuteType extends UserType {

  override def sqlTypes() = Array(Types.SMALLINT)

  override def returnedClass = classOf[Enumeration#Value]

  override def equals(x: Object, y: Object) = x == y

  override def hashCode(x: Object) = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, names: Array[String], session: SessionImplementor, owner: Object): Object = {
    val value = resultSet.getShort(names(0))
    if (resultSet.wasNull()) null
    else new HourMinute(value)
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SessionImplementor): Unit = {
    if (value == null) {
      statement.setNull(index, Types.SMALLINT)
    } else {
      statement.setShort(index, value.asInstanceOf[HourMinute].value)
    }
  }

  override def deepCopy(value: Object): Object = value
  override def isMutable() = false
  override def disassemble(value: Object) = value.asInstanceOf[Serializable]
  override def assemble(cached: java.io.Serializable, owner: Object): Object = cached.asInstanceOf[Object]
  override def replace(original: Object, target: Object, owner: Object) = original
}