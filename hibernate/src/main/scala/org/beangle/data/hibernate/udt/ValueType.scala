/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
import java.lang.reflect.{ Constructor, Field }
import java.sql.{ PreparedStatement, ResultSet, Types }
import java.{ util => ju }

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.usertype.{ ParameterizedType, UserType }

object ValueType {
  val types = Map[Class[_], Int]((classOf[Short], Types.SMALLINT), (classOf[Int], Types.INTEGER),
    (classOf[Long], Types.BIGINT), (classOf[Float], Types.FLOAT), (classOf[Double], Types.DOUBLE),
    (classOf[String], Types.VARCHAR))

  val valueMappers = Map[Class[_], ValueMapper]((classOf[Short], new ShortMapper), (classOf[Int], new IntMapper),
    (classOf[Long], new LongMapper), (classOf[Float], new FloatMapper), (classOf[Double], new DoubleMapper),
    (classOf[String], new StringMapper))

  trait ValueMapper {
    def newInstance(constructor: Constructor[Object], resultSet: ResultSet, name: String): Object = {
      val value = getValue(resultSet, name)
      if (resultSet.wasNull()) {
        return null;
      }
      constructor.newInstance(value)
    }
    def getValue(resultSet: ResultSet, name: String): Object
  }

  class ShortMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, name: String): Object = {
      java.lang.Short.valueOf(resultSet.getShort(name))
    }
  }
  class IntMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, name: String): Object = {
      java.lang.Integer.valueOf(resultSet.getInt(name))
    }
  }
  class LongMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, name: String): Object = {
      java.lang.Long.valueOf(resultSet.getLong(name))
    }
  }
  class FloatMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, name: String): Object = {
      java.lang.Float.valueOf(resultSet.getFloat(name))
    }
  }
  class DoubleMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, name: String): Object = {
      java.lang.Double.valueOf(resultSet.getDouble(name))
    }
  }
  class StringMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, name: String): Object = {
      resultSet.getString(name)
    }
  }
}

class ValueType extends UserType with ParameterizedType {
  var returnedClass: Class[_] = _
  var sqlTypes: Array[Int] = _
  var field: Field = _
  var constructor: Constructor[Object] = _
  var valueMapper: ValueType.ValueMapper = _

  override def equals(x: Object, y: Object): Boolean = {
    x == y
  }

  override def hashCode(x: Object): Int = {
    x.hashCode()
  }

  override def nullSafeGet(resultSet: ResultSet, names: Array[String], session: SessionImplementor, owner: Object): Object = {
    valueMapper.newInstance(constructor, resultSet, names(0))
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SessionImplementor): Unit = {
    if (value == null) {
      statement.setNull(index, sqlTypes(0))
    } else {
      statement.setObject(index, field.get(value))
    }
  }

  override def setParameterValues(parameters: ju.Properties) {
    this.returnedClass = Class.forName(parameters.getProperty("valueClass"))
    var underlyClass: Class[_] = null
    this.returnedClass.getDeclaredFields foreach { f =>
      underlyClass = f.getType
      this.sqlTypes = Array(ValueType.types(underlyClass))
      this.field = f
    }
    if (this.field == null || underlyClass == null)
      throw new RuntimeException(s"Cannot find field for ${this.returnedClass}")
    this.field.setAccessible(true)
    this.constructor = returnedClass.getConstructor(underlyClass).asInstanceOf[Constructor[Object]]
    this.valueMapper = ValueType.valueMappers(underlyClass)
  }

  override def deepCopy(value: Object): Object = value

  override def isMutable() = false

  override def disassemble(value: Object): JSerializable = {
    value.asInstanceOf[JSerializable]
  }
  override def assemble(cached: JSerializable, owner: Object): Object = {
    cached.asInstanceOf[Object]
  }

  override def replace(original: Object, target: Object, owner: Object) = original
}
