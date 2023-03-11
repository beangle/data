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

package org.beangle.data.jdbc.query

import org.beangle.commons.io.IOs
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.SqlTypeMapping

import java.io.*
import java.math.BigDecimal
import java.sql.{Blob, Clob, Date, PreparedStatement, SQLException, Time, Timestamp}
import java.sql.Types.*
import java.time.*
import java.util as ju

object TypeParamSetter {
  def apply(sqlTypeMapping: SqlTypeMapping, params: collection.Seq[Any]): TypeParamSetter = {
    val types = new Array[Int](params.length)
    types.indices foreach { i =>
      types(i) = if (null == params(i)) VARCHAR else sqlTypeMapping.sqlCode(params(i).getClass)
    }
    new TypeParamSetter(params, types)
  }

}

class TypeParamSetter(params: collection.Seq[Any], types: collection.Seq[Int])
  extends (PreparedStatement => Unit) with Logging {
  override def apply(ps: PreparedStatement): Unit = {
    ParamSetter.setParams(ps, params, types)
  }
}

object ParamSetter extends Logging {

  def setParam(stmt: PreparedStatement, index: Int, value: Any, sqltype: Int): Unit = {
    try {
      sqltype match {
        case CHAR | VARCHAR =>
          stmt.setString(index, value.asInstanceOf[String])
        case LONGVARCHAR =>
          stmt.setCharacterStream(index, new StringReader(value.asInstanceOf[String]))

        case BOOLEAN | BIT =>
          value match {
            case b: Boolean => stmt.setBoolean(index, b)
            case i: Number => stmt.setBoolean(index, i.intValue > 0)
          }
        case TINYINT | SMALLINT | INTEGER =>
          stmt.setInt(index, value.asInstanceOf[Number].intValue)
        case BIGINT =>
          stmt.setLong(index, value.asInstanceOf[Number].longValue)

        case FLOAT | DOUBLE =>
          value match {
            case bd: BigDecimal => stmt.setBigDecimal(index, bd)
            case d: Double => stmt.setDouble(index, d)
            case f: Float => stmt.setFloat(index, f)
            case _ => stmt.setObject(index, value, sqltype)
          }
        case NUMERIC | DECIMAL =>
          value match {
            case bd: BigDecimal => stmt.setBigDecimal(index, bd)
            case _ => stmt.setObject(index, value, sqltype)
          }
        case DATE =>
          value match {
            case ld: LocalDate =>
              stmt.setDate(index, Date.valueOf(ld))
            case jd: ju.Date =>
              jd match {
                case jsd: java.sql.Date => stmt.setDate(index, jsd)
                case _ => stmt.setDate(index, new java.sql.Date(jd.getTime))
              }
            case jc: ju.Calendar =>
              stmt.setDate(index, new java.sql.Date(jc.getTime.getTime), jc)
            case ym: YearMonth =>
              stmt.setDate(index, Date.valueOf(LocalDate.of(ym.getYear, ym.getMonth, 1)))
            case _ =>
              stmt.setObject(index, value, DATE)
          }
        case TIME | TIME_WITH_TIMEZONE =>
          value match {
            case lt: LocalTime =>
              stmt.setTime(index, Time.valueOf(lt))
            case jd: ju.Date =>
              value match {
                case t: Time => stmt.setTime(index, t)
                case _ => stmt.setTime(index, new java.sql.Time(jd.getTime))
              }
            case jc: ju.Calendar =>
              stmt.setTime(index, new Time(jc.getTime.getTime), jc)
            case _ =>
              stmt.setObject(index, value, TIME)
          }
        case TIMESTAMP | TIMESTAMP_WITH_TIMEZONE =>
          value match {
            case i: Instant => stmt.setTimestamp(index, Timestamp.from(i))
            case odt: OffsetDateTime => stmt.setTimestamp(index, Timestamp.from(odt.toInstant))
            case ldt: LocalDateTime => stmt.setTimestamp(index, Timestamp.valueOf(ldt))
            case zdt: ZonedDateTime => stmt.setTimestamp(index, Timestamp.valueOf(zdt.toLocalDateTime))
            case ts: Timestamp => stmt.setTimestamp(index, ts)
            case jc: ju.Calendar => stmt.setTimestamp(index, new Timestamp(jc.getTime.getTime), jc)
            case jd: ju.Date => stmt.setTimestamp(index, new Timestamp(jd.getTime))
            case _ => stmt.setObject(index, value, TIMESTAMP)
          }
        case BINARY | VARBINARY | LONGVARBINARY =>
          value match {
            case ab: Array[Byte] =>
              stmt.setBinaryStream(index, new ByteArrayInputStream(ab), ab.length)
            case in: InputStream =>
              val out = new ByteArrayOutputStream()
              IOs.copy(in, out)
              stmt.setBinaryStream(index, in, out.size)
            case _ =>
              throw new RuntimeException("Binary cannot accept type:" + value.getClass)
          }
        case CLOB =>
          if (isStringType(value.getClass)) stmt.setString(index, value.toString)
          else {
            val clb = value.asInstanceOf[Clob]
            stmt.setString(index, clb.getSubString(1, clb.length.asInstanceOf[Int]))
          }
        case BLOB =>
          val in = value match {
            case array: Array[Byte] => new ByteArrayInputStream(array)
            case is: InputStream => is
            case blob: Blob => blob.getBinaryStream
          }
          stmt.setBinaryStream(index, in)
        case _ => if (0 == sqltype) stmt.setObject(index, value) else stmt.setObject(index, value, sqltype)
      }
    } catch {
      case e: Exception => logger.error("set value error", e);
    }
  }

  def setParams(stmt: PreparedStatement, params: collection.Seq[Any], types: collection.Seq[Int]): Unit = {
    val paramsCount = if (params == null) 0 else params.length
    val stmtParamCount = types.length
    val sqltypes = types.toArray

    if (stmtParamCount > paramsCount)
      throw new SQLException("Wrong number of parameters: expected " + stmtParamCount + ", was given " + paramsCount)

    var i = 0
    while (i < stmtParamCount) {
      val index = i + 1
      if (null == params(i)) {
        stmt.setNull(index, if (sqltypes(i) == NULL) VARCHAR else sqltypes(i))
      } else {
        setParam(stmt, index, params(i), sqltypes(i))
      }
      i += 1
    }
  }

  private def isStringType(clazz: Class[_]): Boolean = {
    classOf[CharSequence].isAssignableFrom(clazz) || classOf[StringWriter].isAssignableFrom(clazz)
  }
}
