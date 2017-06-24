package org.beangle.data.jdbc

import java.sql.Types.{ BIGINT, BINARY, BIT, BLOB, BOOLEAN, CHAR, CLOB, DATE, DECIMAL, DOUBLE, FLOAT, INTEGER, LONGVARBINARY, LONGVARCHAR, NULL, NUMERIC, OTHER, SMALLINT, TIME, TIMESTAMP, TINYINT, VARBINARY, VARCHAR }
import java.math.BigInteger
import java.io.StringWriter
import org.beangle.commons.lang.time.WeekState
import org.beangle.commons.lang.time.HourMinute
import java.time.Year
import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.SqlType
import org.beangle.data.jdbc.meta.Engine
import org.beangle.commons.lang.annotation.value

object SqlTypeMapping {
  def DefaultStringSqlType = new SqlType(VARCHAR, "varchar(255)", 255)
}

trait SqlTypeMapping {

  def sqlType(clazz: Class[_]): SqlType

  def sqlCode(clazz: Class[_]): Int
}

class DefaultSqlTypeMapping(engine: Engine) {
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

    (classOf[java.sql.Time], TIME),
    (classOf[java.time.LocalTime], TIME),

    (classOf[java.sql.Timestamp], TIMESTAMP),
    (classOf[java.util.Date], TIMESTAMP),
    (classOf[java.util.Calendar], TIMESTAMP),
    (classOf[java.time.Instant], TIMESTAMP),
    (classOf[java.time.LocalDateTime], TIMESTAMP),
    (classOf[java.time.ZonedDateTime], TIMESTAMP),

    (classOf[java.time.Duration], BIGINT),
    (classOf[HourMinute], SMALLINT),
    (classOf[WeekState], BIGINT),
    (classOf[Year], INTEGER),

    (classOf[java.sql.Clob], CLOB),
    (classOf[java.sql.Blob], BLOB))

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
          case Some((clazz, tc)) => tc
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
              concretTypes.get(find).getOrElse(OTHER)
            } else if (clazz.getName.contains("$")) {
              val containerClass = Class.forName(Strings.substringBefore(clazz.getName, "$") + "$")
              if (classOf[Enumeration].isAssignableFrom(containerClass)) {
                INTEGER
              } else {
                throw new RuntimeException(s"Cannot find sqltype for ${clazz.getName}")
              }
            } else {
              OTHER
            }
        }
    }
  }

  def sqlType(clazz: Class[_]): SqlType = {
    val sqlType = engine.toType(sqlCode(clazz))
    if (sqlType.code == VARCHAR) sqlType.length = Some(255)
    sqlType
  }
}