/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate.udt

import java.io.{ Serializable => JSerializable }
import java.sql.{ PreparedStatement, ResultSet }

import org.beangle.commons.lang.{ JChar, JByte, JBoolean, JInt, JShort, JLong, JFloat, JDouble, Primitives, Objects }
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.commons.bean.Properties
import org.beangle.data.model.Entity
import org.beangle.data.model.bind.Jpas
import org.hibernate.`type`.AbstractSingleColumnStandardBasicType
import org.hibernate.`type`.StandardBasicTypes.{ BYTE, CHARACTER, DOUBLE, FLOAT, INTEGER, SHORT, LONG, BOOLEAN, STRING, DATE, TIMESTAMP }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.{ ParameterizedType, UserType }

object OptionBasicType {
  val java2HibernateTypes: Map[Class[_], AbstractSingleColumnStandardBasicType[_]] =
    Map((classOf[JChar], CHARACTER), (classOf[String], STRING),
      (classOf[JByte], BYTE), (classOf[JBoolean], BOOLEAN),
      (classOf[JInt], INTEGER), (classOf[JShort], SHORT), (classOf[JLong], LONG),
      (classOf[JFloat], FLOAT), (classOf[JDouble], DOUBLE),
      (classOf[java.util.Date], TIMESTAMP), (classOf[java.sql.Date], DATE),
      (classOf[java.sql.Timestamp], TIMESTAMP))
}

abstract class OptionBasicType[T](clazz: Class[T]) extends UserType {
  import OptionBasicType._

  val inner = java2HibernateTypes(clazz)

  def sqlTypes: Array[Int] = {
    Array(inner.sqlType)
  }

  def returnedClass = classOf[Option[T]]

  final def nullSafeGet(rs: ResultSet, names: Array[String], session: SessionImplementor, owner: Object): AnyRef = {
    val x = inner.nullSafeGet(rs, names, session, owner)
    if (x == null) None else Some(x)
  }

  final def nullSafeSet(ps: PreparedStatement, value: Object, index: Int, session: SessionImplementor): Unit = {
    val v = value match {
      case null    => null
      case None    => null
      case Some(x) => x
      case _       => value
    }
    inner.nullSafeSet(ps, v, index, session)
  }

  def isMutable = false

  def equals(x: Object, y: Object) = {
    Objects.equals(x, y)
  }

  def hashCode(x: Object) = x.hashCode

  def deepCopy(value: Object) = value

  def replace(original: Object, target: Object, owner: Object) = original

  def disassemble(value: Object) = value.asInstanceOf[JSerializable]

  def assemble(cached: JSerializable, owner: Object): Object = cached.asInstanceOf[Object]
}

class OptionCharType extends OptionBasicType(classOf[JChar])

class OptionByteType extends OptionBasicType(classOf[JByte])

class OptionShortType extends OptionBasicType(classOf[JShort])

class OptionIntType extends OptionBasicType(classOf[JInt])

class OptionBooleanType extends OptionBasicType(classOf[JBoolean])

class OptionLongType extends OptionBasicType(classOf[JLong])

class OptionFloatType extends OptionBasicType(classOf[JFloat])

class OptionDoubleType extends OptionBasicType(classOf[JDouble])

class OptionStringType extends OptionBasicType(classOf[String])

class OptionJuDateType extends OptionBasicType(classOf[java.util.Date])

class OptionJsDateType extends OptionBasicType(classOf[java.sql.Date])

class OptionJsTimestampType extends OptionBasicType(classOf[java.sql.Timestamp])

object OptionEntityType {
  val EntityClassParamName = "entityClass"
}
class OptionEntityType extends UserType with ParameterizedType {
  import OptionBasicType._
  import OptionEntityType._

  var inner: AbstractSingleColumnStandardBasicType[_] = _

  var entityName: String = _

  var clazz: Class[_] = _

  override def setParameterValues(parameters: java.util.Properties) {
    var entityClass = parameters.getProperty(EntityClassParamName)
    clazz = Class.forName(entityClass)
    entityName = Jpas.findEntityName(clazz)
    inner = java2HibernateTypes(Primitives.wrap(BeanInfos.get(clazz).getPropertyType("id").get))
  }

  def sqlTypes: Array[Int] = {
    Array(inner.sqlType)
  }

  def returnedClass = classOf[Option[_]]

  final def nullSafeGet(rs: ResultSet, names: Array[String], session: SessionImplementor, owner: Object): AnyRef = {
    val x = inner.nullSafeGet(rs, names, session, owner).asInstanceOf[JSerializable]
    if (x == null) {
      None
    } else {
      val persister = session.getFactory.getEntityPersister(entityName)
      Some(persister.createProxy(x, session))
    }
  }

  final def nullSafeSet(ps: PreparedStatement, value: Object, index: Int, session: SessionImplementor): Unit = {
    val id: AnyRef =
      value match {
        case null    => null
        case None    => null
        case Some(x) => Properties.get(x, "id")
        case _       => Properties.get(value, "id")
      }
    inner.nullSafeSet(ps, id, index, session)
  }

  def isMutable = false

  def equals(x: Object, y: Object): Boolean = {
    Objects.equals(x, y)
  }

  def hashCode(x: Object) = x.hashCode

  def deepCopy(value: Object) = value

  def replace(original: Object, target: Object, owner: Object) = original

  def disassemble(value: Object) = value.asInstanceOf[JSerializable]

  def assemble(cached: JSerializable, owner: Object): Object = cached.asInstanceOf[Object]
}
