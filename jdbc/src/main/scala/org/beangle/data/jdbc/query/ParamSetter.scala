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
package org.beangle.data.jdbc.query

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream, StringReader, StringWriter }
import java.math.BigDecimal
import java.sql.{ Blob, Clob, Date, PreparedStatement, SQLException, Time, Timestamp }
import java.sql.Types.{ BIGINT, BINARY, BIT, BLOB, BOOLEAN, CHAR, CLOB, DATE, DECIMAL, DOUBLE, FLOAT, INTEGER, LONGVARBINARY, LONGVARCHAR, NULL, NUMERIC, SMALLINT, TIME, TIMESTAMP, TINYINT, VARBINARY, VARCHAR }
import java.time.{ Instant, LocalDate, LocalDateTime, LocalTime, ZonedDateTime }
import java.{ util => ju }

import org.beangle.commons.io.IOs
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.SqlTypeMapping

object TypeParamSetter {
  def apply(sqlTypeMapping: SqlTypeMapping, params: Seq[Any]): TypeParamSetter = {
    val types = new Array[Int](params.length)
    for (i <- 0 until types.length) {
      types(i) = if (null == params(i)) VARCHAR else sqlTypeMapping.sqlCode(params(i).getClass)
    }
    new TypeParamSetter(params, types)
  }

}

class TypeParamSetter(params: Seq[Any], types: Seq[Int])
    extends Function1[PreparedStatement, Unit] with Logging {
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
            case i: Number  => stmt.setBoolean(index, i.intValue > 0)
          }
        case TINYINT | SMALLINT | INTEGER =>
          stmt.setInt(index, value.asInstanceOf[Number].intValue)
        case BIGINT =>
          stmt.setLong(index, value.asInstanceOf[Number].longValue)

        case FLOAT | DOUBLE =>
          if (value.isInstanceOf[BigDecimal]) {
            stmt.setBigDecimal(index, value.asInstanceOf[BigDecimal])
          } else {
            stmt.setDouble(index, value.asInstanceOf[Double])
          }

        case NUMERIC | DECIMAL => {
          if (value.isInstanceOf[BigDecimal]) {
            stmt.setBigDecimal(index, value.asInstanceOf[BigDecimal])
          } else {
            stmt.setObject(index, value, sqltype)
          }
        }

        case DATE => {
          value match {
            case ld: LocalDate =>
              stmt.setDate(index, Date.valueOf(ld))
            case jd: ju.Date =>
              if (jd.isInstanceOf[Date]) stmt.setDate(index, jd.asInstanceOf[Date])
              else stmt.setDate(index, new java.sql.Date(jd.getTime))
            case jc: ju.Calendar =>
              stmt.setDate(index, new java.sql.Date(jc.getTime.getTime), jc)
            case _ =>
              stmt.setObject(index, value, DATE);
          }
        }
        case TIME => {
          value match {
            case lt: LocalTime =>
              stmt.setTime(index, Time.valueOf(lt))
            case jd: ju.Date =>
              if (value.isInstanceOf[Time]) stmt.setTime(index, value.asInstanceOf[Time])
              else stmt.setTime(index, new java.sql.Time(jd.getTime))
            case jc: ju.Calendar =>
              stmt.setTime(index, new Time(jc.getTime.getTime), jc)
            case _ =>
              stmt.setObject(index, value, TIME);
          }
        }
        case TIMESTAMP => {
          value match {
            case i: Instant =>
              stmt.setTimestamp(index, Timestamp.from(i))
            case ldt: LocalDateTime =>
              stmt.setTimestamp(index, Timestamp.valueOf(ldt))
            case zdt: ZonedDateTime =>
              stmt.setTimestamp(index, Timestamp.valueOf(zdt.toLocalDateTime))
            case ts: Timestamp =>
              stmt.setTimestamp(index, ts)
            case jc: ju.Calendar =>
              stmt.setTimestamp(index, new Timestamp(jc.getTime.getTime), jc)
            case jd: ju.Date =>
              stmt.setTimestamp(index, new Timestamp(jd.getTime))
            case _ => stmt.setObject(index, value, TIMESTAMP)
          }
        }
        case BINARY | VARBINARY | LONGVARBINARY => {
          if (value.isInstanceOf[Array[Byte]]) {
            val bytes = value.asInstanceOf[Array[Byte]]
            stmt.setBinaryStream(index, new ByteArrayInputStream(bytes), bytes.length)
          } else {
            val in = value.asInstanceOf[InputStream]
            val out = new ByteArrayOutputStream()
            IOs.copy(in, out)
            stmt.setBinaryStream(index, in, out.size)
          }
        }
        case CLOB => {
          if (isStringType(value.getClass)) stmt.setString(index, value.toString)
          else {
            //FIXME workround Method org.postgresql.jdbc4.Jdbc4PreparedStatement.setAsciiStream(int, InputStream) is not yet implemented.
            val clb = value.asInstanceOf[Clob]
            val out = new ByteArrayOutputStream()
            IOs.copy(clb.getAsciiStream, out)
            stmt.setAsciiStream(index, clb.getAsciiStream, out.size())
          }
        }
        case BLOB => {
          val in = value.asInstanceOf[Blob].getBinaryStream
          val out = new ByteArrayOutputStream()
          IOs.copy(in, out)
          stmt.setBinaryStream(index, in, out.size)
        }
        case _ => if (0 == sqltype) stmt.setObject(index, value) else stmt.setObject(index, value, sqltype)
      }
    } catch {
      case e: Exception => logger.error("set value error", e);
    }
  }

  def setParams(stmt: PreparedStatement, params: Seq[Any], types: Seq[Int]): Unit = {
    val paramsCount = if (params == null) 0 else params.length
    var stmtParamCount = types.length
    var sqltypes = types.toArray

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
