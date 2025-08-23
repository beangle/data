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

package org.beangle.data.orm.hibernate.jdbc

import org.beangle.jdbc.engine.Engine
import org.hibernate.`type`.descriptor.java.JavaType
import org.hibernate.`type`.descriptor.jdbc.JdbcType
import org.hibernate.`type`.descriptor.{ValueBinder, ValueExtractor, WrapperOptions}

import java.sql.{CallableStatement, PreparedStatement, ResultSet, Types}

object JsonAccessor {

  def getJdbcType(engine: Engine): JdbcType = {
    if engine.supportJsonType then new NativeJsonJdbcType(engine) else new StringJsonJdbcType()
  }

  def getObjectBinder[X](javaType: JavaType[X], engine: Engine): ValueBinder[X] = {
    new ObjectBinder(javaType, engine)
  }

  def getStringBinder[X](javaType: JavaType[X], nationalized: Boolean = false): ValueBinder[X] = {
    if nationalized then new NStringBinder(javaType) else new StringBinder(javaType)
  }

  def getExtractor[X](javaType: JavaType[X], nationalized: Boolean = false): ValueExtractor[X] = {
    if nationalized then new NStringExtractor(javaType) else new StringExtractor[X](javaType)
  }

  /** Support extract string from varchar/longvharcar/clob/json
   *
   * @param javaType
   * @tparam X
   */
  class StringExtractor[X](javaType: JavaType[X]) extends ValueExtractor[X] {
    override def extract(rs: ResultSet, paramIndex: Int, options: WrapperOptions): X = {
      javaType.wrap(rs.getString(paramIndex), options)
    }

    override def extract(statement: CallableStatement, index: Int, options: WrapperOptions): X = {
      javaType.wrap(statement.getString(index), options)
    }

    override def extract(statement: CallableStatement, name: String, options: WrapperOptions): X = {
      javaType.wrap(statement.getString(name), options)
    }
  }

  /** Support extract string from nvarhar/longnvarchar/NClob
   *
   * @param javaType
   * @tparam X
   */
  class NStringExtractor[X](javaType: JavaType[X]) extends ValueExtractor[X] {
    override def extract(rs: ResultSet, paramIndex: Int, options: WrapperOptions): X = {
      javaType.wrap(rs.getNString(paramIndex), options)
    }

    override def extract(statement: CallableStatement, index: Int, options: WrapperOptions): X = {
      javaType.wrap(statement.getNString(index), options)
    }

    override def extract(statement: CallableStatement, name: String, options: WrapperOptions): X = {
      javaType.wrap(statement.getNString(name), options)
    }
  }

  /** Save json using setNString
   *
   * @param javaType
   * @tparam X
   */
  class NStringBinder[X](javaType: JavaType[X]) extends ValueBinder[X] {
    override def bind(st: PreparedStatement, value: X, index: Int, options: WrapperOptions): Unit = {
      st.setNString(index, javaType.unwrap(value, classOf[String], options))
    }

    override def bind(st: CallableStatement, value: X, name: String, options: WrapperOptions): Unit = {
      st.setNString(name, javaType.unwrap(value, classOf[String], options))
    }
  }

  /** Save json as string directly
   *
   * @param javaType
   * @tparam X
   */
  class StringBinder[X](javaType: JavaType[X]) extends ValueBinder[X] {
    override def bind(st: PreparedStatement, value: X, index: Int, options: WrapperOptions): Unit = {
      st.setString(index, javaType.unwrap(value, classOf[String], options))
    }

    override def bind(st: CallableStatement, value: X, name: String, options: WrapperOptions): Unit = {
      st.setString(name, javaType.unwrap(value, classOf[String], options))
    }
  }

  /** Save json as object by engine wrapper
   *
   * @param javaType
   * @param engine
   * @tparam X
   */
  class ObjectBinder[X](javaType: JavaType[X], engine: Engine) extends ValueBinder[X] {
    override def bind(st: PreparedStatement, value: X, index: Int, options: WrapperOptions): Unit = {
      st.setObject(index, engine.mkJsonObject(javaType.unwrap(value, classOf[String], options)), Types.OTHER)
    }

    override def bind(st: CallableStatement, value: X, name: String, options: WrapperOptions): Unit = {
      st.setObject(name, engine.mkJsonObject(javaType.unwrap(value, classOf[String], options)), Types.OTHER)
    }
  }
}
