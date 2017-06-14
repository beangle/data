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
package org.beangle.data.orm

import java.sql.{ Date, Time, Timestamp, Types }
import java.util.{ Calendar, Date }

import org.beangle.data.jdbc.meta.{ Engine, SqlType }
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.time.WeekState

object SqlTypeMapping {
  def DefaultStringSqlType = new SqlType(Types.VARCHAR, "varchar(255)", 255)
}

trait SqlTypeMapping {
  def sqlType(clazz: Class[_]): SqlType
}

class DefaultSqlTypeMapping(engine: Engine) {
  val class2Types = new collection.mutable.HashMap[Class[_], Int]

  registerBuiltin()

  private def registerBuiltin() {
    class2Types.put(classOf[Boolean], Types.BOOLEAN)
    class2Types.put(classOf[Char], Types.CHAR)
    class2Types.put(classOf[Character], Types.CHAR)
    class2Types.put(classOf[Short], Types.SMALLINT)
    class2Types.put(classOf[Int], Types.INTEGER)
    class2Types.put(classOf[Long], Types.BIGINT)
    class2Types.put(classOf[java.lang.Short], Types.SMALLINT)
    class2Types.put(classOf[java.lang.Integer], Types.INTEGER)
    class2Types.put(classOf[java.lang.Long], Types.BIGINT)
    class2Types.put(classOf[String], Types.VARCHAR)
    class2Types.put(classOf[java.sql.Date], Types.DATE)
    class2Types.put(classOf[java.sql.Time], Types.TIME)
    class2Types.put(classOf[java.sql.Timestamp], Types.TIMESTAMP)
    class2Types.put(classOf[java.util.Date], Types.TIMESTAMP)
    class2Types.put(classOf[java.util.Calendar], Types.TIMESTAMP)
    class2Types.put(classOf[java.time.Instant], Types.TIMESTAMP)
    class2Types.put(classOf[java.time.LocalDate], Types.DATE)
    class2Types.put(classOf[java.time.LocalDateTime], Types.TIMESTAMP)
    class2Types.put(classOf[java.time.ZonedDateTime], Types.TIMESTAMP)
    class2Types.put(classOf[WeekState], Types.BIGINT)
  }

  def sqlType(clazz: Class[_]): SqlType = {
    class2Types.get(clazz) match {
      case Some(t) =>
        val sqlType = engine.toType(t)
        if (sqlType.code == Types.VARCHAR) sqlType.length = Some(255)
        sqlType
      case None =>
        if (clazz.getName.contains("$")) {
          val containerClass = Class.forName(Strings.substringBefore(clazz.getName, "$") + "$")
          if (classOf[Enumeration].isAssignableFrom(containerClass)) {
            engine.toType(Types.INTEGER)
          } else {
            throw new RuntimeException(s"Cannot find sqltype for ${clazz.getName}")
          }
        } else {
          throw new RuntimeException(s"Cannot find sqltype for ${clazz.getName}")
        }
    }
  }
}
