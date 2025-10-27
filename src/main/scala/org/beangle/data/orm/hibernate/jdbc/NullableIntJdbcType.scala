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

import org.hibernate.`type`.descriptor.java.JavaType
import org.hibernate.`type`.descriptor.jdbc.internal.JdbcLiteralFormatterNumericData
import org.hibernate.`type`.descriptor.jdbc.{BasicBinder, BasicExtractor, JdbcLiteralFormatter, JdbcType}
import org.hibernate.`type`.descriptor.{ValueBinder, ValueExtractor, WrapperOptions}
import org.hibernate.`type`.spi.TypeConfiguration

import java.sql.*
import scala.annotation.nowarn

/** Same as hibernate IntegerJdbcType,but invoke wasNull before javaType.wrap
 */
object NullableIntJdbcType extends JdbcType {

  override def getJdbcTypeCode: Int = Types.INTEGER

  override def getFriendlyName = "INTEGER"

  override def toString = "IntegerTypeDescriptor"

  @nowarn
  override def getJdbcRecommendedJavaTypeMapping[T](length: Integer, scale: Integer,
                                                    typeConfiguration: TypeConfiguration): JavaType[T] = {
    typeConfiguration.getJavaTypeRegistry.getDescriptor(classOf[Integer])
  }

  override def getJdbcLiteralFormatter[T](javaType: JavaType[T]): JdbcLiteralFormatter[T] = {
    new JdbcLiteralFormatterNumericData[T](javaType, classOf[Integer])
  }

  override def getPreferredJavaTypeClass(options: WrapperOptions): Class[_] = {
    classOf[Integer]
  }

  override def getBinder[X](javaType: JavaType[X]): ValueBinder[X] = {
    new BasicBinder[X](javaType, this) {
      override protected def doBind(st: PreparedStatement, value: X, index: Int, options: WrapperOptions): Unit = {
        st.setInt(index, javaType.unwrap(value, classOf[Int], options))
      }

      override protected def doBind(st: CallableStatement, value: X, name: String, options: WrapperOptions): Unit = {
        st.setInt(name, javaType.unwrap(value, classOf[Integer], options).intValue)
      }
    }
  }

  /** Different from Hibernate:Extract null value before javatype.wrap method
   *
   * @param javaType
   * @tparam X
   * @return
   */
  override def getExtractor[X](javaType: JavaType[X]): ValueExtractor[X] = {
    new BasicExtractor[X](javaType, this) {
      override protected def doExtract(rs: ResultSet, paramIndex: Int, options: WrapperOptions): X = {
        val i = rs.getInt(paramIndex)
        if rs.wasNull then null.asInstanceOf[X] else javaType.wrap(i, options)
      }

      override protected def doExtract(statement: CallableStatement, index: Int, options: WrapperOptions): X = {
        val i = statement.getInt(index)
        if statement.wasNull then null.asInstanceOf[X] else javaType.wrap(i, options)
      }

      override protected def doExtract(statement: CallableStatement, name: String, options: WrapperOptions): X = {
        val i = statement.getInt(name)
        if statement.wasNull then null.asInstanceOf[X] else javaType.wrap(i, options)
      }
    }
  }
}
