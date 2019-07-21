/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc

import java.math.BigInteger
import java.sql.Types.{ BIGINT, BLOB, BOOLEAN, CHAR, CLOB, DATE, DECIMAL, DOUBLE, FLOAT, INTEGER, NUMERIC, SMALLINT, TIME, TIMESTAMP, TINYINT, VARCHAR, VARBINARY }
import java.time.Year

import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.value
import org.beangle.commons.lang.time.{ HourMinute, WeekState }
import org.beangle.data.jdbc.meta.{ Engine, SqlType }

object SqlTypeMapping {
  def DefaultStringSqlType = new SqlType(VARCHAR, "varchar(255)", 255)
}

trait SqlTypeMapping {

  def sqlType(clazz: Class[_]): SqlType

  def sqlCode(clazz: Class[_]): Int
}

class DefaultSqlTypeMapping(engine: Engine) extends SqlTypeMapping {
  private val concretTypes: Map[Class[_], Int] = Map(
    (classOf[Boolean], BOOLEAN),
    (classOf[Byte], TINYINT),

    (classOf[Short], SMALLINT),
    (classOf[java.lang.Short], SMALLINT),
    (classOf[Int], INTEGER),
    (classOf[Integer], INTEGER),
    (classOf[Long], BIGINT),
    (classOf[java.lang.Long], BIGINT),

    (classOf[BigInteger], BIGINT),
    (classOf[Float], FLOAT),
    (classOf[java.lang.Float], FLOAT),
    (classOf[Double], DOUBLE),
    (classOf[java.lang.Double], DOUBLE),
    (classOf[BigDecimal], DECIMAL),

    (classOf[Char], CHAR),
    (classOf[Character], CHAR),
    (classOf[String], VARCHAR),

    (classOf[java.sql.Date], DATE),
    (classOf[java.time.LocalDate], DATE),
    (classOf[java.time.YearMonth], DATE),

    (classOf[java.sql.Time], TIME),
    (classOf[java.time.LocalTime], TIME),

    (classOf[java.sql.Timestamp], TIMESTAMP),
    (classOf[java.util.Date], TIMESTAMP),
    (classOf[java.util.Calendar], TIMESTAMP),
    (classOf[java.time.Instant], TIMESTAMP),
    (classOf[java.time.LocalDateTime], TIMESTAMP),
    (classOf[java.time.ZonedDateTime], TIMESTAMP),

    (classOf[java.util.Locale], VARCHAR),

    (classOf[java.time.Duration], BIGINT),
    (classOf[HourMinute], SMALLINT),
    (classOf[WeekState], BIGINT),
    (classOf[Year], INTEGER),

    (classOf[java.sql.Clob], CLOB),
    (classOf[java.sql.Blob], BLOB),
    (classOf[Array[Byte]], VARBINARY))

  private val generalTypes: Map[Class[_], Int] = Map(
    (classOf[java.util.Date], TIMESTAMP),
    (classOf[CharSequence], VARCHAR),
    (classOf[Number], NUMERIC))

  def sqlCode(clazz: Class[_]): Int = {
    concretTypes.get(clazz) match {
      case Some(c) => c
      case None =>
        val finded = generalTypes.find(_._1.isAssignableFrom(clazz))
        finded match {
          case Some((_, tc)) => tc
          case None =>
            if (clazz.isAnnotationPresent(classOf[value])) {
              val ctors = clazz.getConstructors
              var find: Class[_] = null
              var i = 0
              while ((find eq null) && i < ctors.length) {
                val ctor = ctors(i)
                val params = ctor.getParameters
                if (params.length == 1) find = params(0).getType
                i += 1
              }
              concretTypes.getOrElse(find,raiseMappingError(clazz))
            } else if (clazz.getName.contains("$")) {
              // convert A$Val to A$
              val containerClass = Class.forName(Strings.substringBefore(clazz.getName, "$") + "$")
              if (classOf[Enumeration].isAssignableFrom(containerClass)) {
                INTEGER
              } else {
                raiseMappingError(clazz)
              }
            } else {
              raiseMappingError(clazz)
            }
        }
    }
  }

  private def raiseMappingError(clazz: Class[_]): Int = {
    throw new RuntimeException(s"Cannot find sqltype for ${clazz.getName}")
  }

  def sqlType(clazz: Class[_]): SqlType = {
    val sqlTypeCode = sqlCode(clazz)
    if (sqlTypeCode == VARCHAR) engine.toType(sqlTypeCode, 255) else engine.toType(sqlTypeCode)
  }
}
