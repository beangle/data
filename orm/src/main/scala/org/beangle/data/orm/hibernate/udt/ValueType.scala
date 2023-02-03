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

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.{ParameterizedType, UserType}

import java.io.Serializable as JSerializable
import java.lang.reflect.{Constructor, Field}
import java.sql.{PreparedStatement, ResultSet, Types}
import java.util as ju

object ValueType {
  val types = Map[Class[_], Int]((classOf[Short], Types.SMALLINT), (classOf[Int], Types.INTEGER),
    (classOf[Long], Types.BIGINT), (classOf[Float], Types.FLOAT), (classOf[Double], Types.DOUBLE),
    (classOf[String], Types.VARCHAR))

  val valueMappers = Map[Class[_], ValueMapper]((classOf[Short], new ShortMapper), (classOf[Int], new IntMapper),
    (classOf[Long], new LongMapper), (classOf[Float], new FloatMapper), (classOf[Double], new DoubleMapper),
    (classOf[String], new StringMapper))

  trait ValueMapper {
    def newInstance(constructor: Constructor[Object], resultSet: ResultSet, position: Int): Object = {
      val value = getValue(resultSet, position)
      if (resultSet.wasNull()) {
        return null;
      }
      constructor.newInstance(value)
    }

    def getValue(resultSet: ResultSet, position: Int): Object
  }

  class ShortMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, position: Int): Object = {
      java.lang.Short.valueOf(resultSet.getShort(position))
    }
  }

  class IntMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, position: Int): Object = {
      java.lang.Integer.valueOf(resultSet.getInt(position))
    }
  }

  class LongMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, position: Int): Object = {
      java.lang.Long.valueOf(resultSet.getLong(position))
    }
  }

  class FloatMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, position: Int): Object = {
      java.lang.Float.valueOf(resultSet.getFloat(position))
    }
  }

  class DoubleMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, position: Int): Object = {
      java.lang.Double.valueOf(resultSet.getDouble(position))
    }
  }

  class StringMapper extends ValueMapper {
    override def getValue(resultSet: ResultSet, position: Int): Object = {
      resultSet.getString(position)
    }
  }

}

class ValueType extends UserType[Object] with ParameterizedType {
  var returnedClass: Class[Object] = _
  var sqlType: Int = _
  var field: Field = _
  var constructor: Constructor[Object] = _
  var valueMapper: ValueType.ValueMapper = _

  override def getSqlType: Int = sqlType
  override def equals(x: Object, y: Object): Boolean = x == y

  override def hashCode(x: Object): Int = x.hashCode()

  override def nullSafeGet(resultSet: ResultSet, position: Int, session: SharedSessionContractImplementor, owner: Object): Object = {
    valueMapper.newInstance(constructor, resultSet, position)
  }

  override def nullSafeSet(statement: PreparedStatement, value: Object, index: Int, session: SharedSessionContractImplementor): Unit = {
    if (value == null) {
      statement.setNull(index, sqlType)
    } else {
      statement.setObject(index, field.get(value))
    }
  }

  override def setParameterValues(parameters: ju.Properties): Unit = {
    this.returnedClass = Class.forName(parameters.getProperty("valueClass")).asInstanceOf[Class[Object]]
    var underlyClass: Class[_] = null
    this.returnedClass.getDeclaredFields foreach { f =>
      underlyClass = f.getType
      this.sqlType = ValueType.types(underlyClass)
      this.field = f
    }
    if (this.field == null || underlyClass == null)
      throw new RuntimeException(s"Cannot find field for ${this.returnedClass}")
    this.field.setAccessible(true)
    this.constructor = returnedClass.getConstructor(underlyClass).asInstanceOf[Constructor[Object]]
    this.valueMapper = ValueType.valueMappers(underlyClass)
  }

  override def deepCopy(value: Object): Object = value

  override def isMutable = false

  override def disassemble(value: Object): JSerializable = {
    value.asInstanceOf[JSerializable]
  }

  override def assemble(cached: JSerializable, owner: Object): Object = {
    cached.asInstanceOf[Object]
  }

  override def replace(original: Object, target: Object, owner: Object) = original
}
