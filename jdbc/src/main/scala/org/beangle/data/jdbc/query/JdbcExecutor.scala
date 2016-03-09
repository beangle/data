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
package org.beangle.data.jdbc.query

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream, StringReader, StringWriter }
import java.lang.reflect.Method
import java.math.{ BigDecimal, BigInteger }
import java.sql.{ BatchUpdateException, Blob, Clob, Connection, Date, PreparedStatement, ResultSet, SQLException, Time, Timestamp }
import java.sql.Types.{ BIGINT, BINARY, BIT, BLOB, BOOLEAN, CHAR, CLOB, DATE, DECIMAL, DOUBLE, FLOAT, INTEGER, LONGVARBINARY, LONGVARCHAR, NULL, NUMERIC, OTHER, SMALLINT, TIME, TIMESTAMP, TINYINT, VARBINARY, VARCHAR }
import java.{ util => ju }

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.{ ClassLoaders, Strings }
import org.beangle.commons.logging.Logging

import javax.sql.DataSource

object JdbcExecutor {
  var oracleTimestampMethod: Method = _
  try {
    val clz = ClassLoaders.load("oracle.sql.TIMESTAMP")
    oracleTimestampMethod = clz.getMethod("timestampValue")
  } catch {
    case e: Exception =>
  }

  val objectTypeToSqlTypeMap: Map[Class[_], Int] = Map((classOf[Boolean], BOOLEAN),
    (classOf[Byte] -> TINYINT),
    (classOf[Short], SMALLINT),
    (classOf[Integer], INTEGER),
    (classOf[Long], BIGINT),
    (classOf[BigInteger], BIGINT),
    (classOf[Float], FLOAT),
    (classOf[Double], DOUBLE),
    (classOf[BigDecimal], DECIMAL),
    (classOf[java.sql.Date], DATE),
    (classOf[java.sql.Time], TIME),
    (classOf[java.sql.Timestamp], TIMESTAMP),
    (classOf[ju.Date], TIMESTAMP),
    (classOf[java.sql.Clob], CLOB),
    (classOf[java.sql.Blob], BLOB))

  def isStringType(clazz: Class[_]): Boolean = {
    classOf[CharSequence].isAssignableFrom(clazz) || classOf[StringWriter].isAssignableFrom(clazz)
  }
  def isDateType(clazz: Class[_]): Boolean = {
    classOf[ju.Date].isAssignableFrom(clazz) &&
      !(classOf[java.sql.Date].isAssignableFrom(clazz) ||
        classOf[java.sql.Time].isAssignableFrom(clazz) ||
        classOf[java.sql.Timestamp].isAssignableFrom(clazz))
  }
  def toSqlType(clazz: Class[_]): Int = {
    objectTypeToSqlTypeMap.get(clazz) match {
      case Some(sqltype) => sqltype
      case None => {
        if (classOf[Number].isAssignableFrom(clazz))
          NUMERIC
        else if (isStringType(clazz)) VARCHAR
        else if (isDateType(clazz) || classOf[ju.Calendar].isAssignableFrom(clazz)) {
          TIMESTAMP
        } else OTHER
      }
    }
  }
}

class JdbcExecutor(dataSource: DataSource) extends Logging {

  import JdbcExecutor._
  var pmdKnownBroken: Boolean = false
  var showSql = false
  def queryForInt(sql: String): Int = query(sql).head.head.asInstanceOf[Number].intValue
  def queryForLong(sql: String): Long = query(sql).head.head.asInstanceOf[Number].longValue

  def getConnection(): Connection = dataSource.getConnection()

  def query(sql: String, params: Any*): Seq[Array[Any]] = {
    if (showSql) println("JdbcExecutor:" + sql)
    val conn = getConnection()
    var stmt: PreparedStatement = null
    var rs: ResultSet = null
    try {
      stmt = conn.prepareStatement(sql)
      setParams(stmt, params, null)
      rs = stmt.executeQuery()
      convertToSeq(rs)
    } catch {
      case e: SQLException => rethrow(e, sql, params); List.empty
    } finally {
      if (null != rs) rs.close()
      if (null != stmt) stmt.close()
      conn.close()
    }
  }

  def update(sql: String, params: Any*): Int = {
    if (showSql) println("JdbcExecutor:" + sql)
    var stmt: PreparedStatement = null
    val conn = getConnection()
    if (conn.getAutoCommit()) conn.setAutoCommit(false)
    var rows = 0
    try {
      stmt = conn.prepareStatement(sql)
      setParams(stmt, params, null)
      rows = stmt.executeUpdate()
      stmt.close()
      stmt = null
      conn.commit()
    } catch {
      case e: SQLException => {
        conn.rollback()
        rethrow(e, sql, params)
      }
    } finally {
      if (null != stmt) stmt.close()
      conn.close()
    }
    rows
  }

  def batch(sql: String, datas: Seq[Array[_]], types: Seq[Int]): Seq[Int] = {
    if (showSql) println("JdbcExecutor:" + sql)
    var stmt: PreparedStatement = null
    val conn = getConnection()
    if (conn.getAutoCommit()) conn.setAutoCommit(false)
    val rows = new collection.mutable.ListBuffer[Int]
    var curParam: Seq[_] = null
    try {
      stmt = conn.prepareStatement(sql)
      for (param <- datas) {
        curParam = param
        setParams(stmt, param, types)
        stmt.addBatch()
      }
      rows ++= stmt.executeBatch()
      conn.commit()
    } catch {
      case be: BatchUpdateException => {
        conn.rollback()
        rethrow(be.getNextException, sql, curParam)
      }
      case e: SQLException => {
        conn.rollback()
        rethrow(e, sql, curParam)
      }
    } finally {
      stmt.close()
      conn.close()
    }
    rows.toList
  }

  def setParams(stmt: PreparedStatement, params: Seq[Any], types: Seq[Int]) {
    // check the parameter count, if we can
    val paramsCount = if (params == null) 0 else params.length
    var stmtParamCount = 0
    var sqltypes: Array[Int] = null

    if (null != types && !types.isEmpty) {
      stmtParamCount = types.length
      sqltypes = types.toArray
    } else {
      stmtParamCount = if (!pmdKnownBroken) stmt.getParameterMetaData().getParameterCount else params.length
      sqltypes = new Array[Int](stmtParamCount)
      for (i <- 0 until stmtParamCount) sqltypes(i) = NULL

      if (!pmdKnownBroken) {
        var pmd = stmt.getParameterMetaData()
        try {
          for (i <- 0 until stmtParamCount) sqltypes(i) = pmd.getParameterType(i + 1)
        } catch {
          case e: SQLException => {
            pmdKnownBroken = true
            for (i <- 0 until stmtParamCount)
              sqltypes(i) = if (null == params(i)) VARCHAR else JdbcExecutor.toSqlType(params(i).getClass)
          }
        }
      } else {
        for (i <- 0 until stmtParamCount)
          sqltypes(i) = if (null == params(i)) VARCHAR else JdbcExecutor.toSqlType(params(i).getClass)
      }
    }

    if (stmtParamCount > paramsCount)
      throw new SQLException("Wrong number of parameters: expected " + stmtParamCount + ", was given " + paramsCount)

    var i = 0
    while (i < stmtParamCount) {
      val index = i + 1
      if (null == params(i)) {
        stmt.setNull(index, if (sqltypes(i) == NULL) VARCHAR else sqltypes(i))
      } else {
        val value = params(i)
        try {
          val sqltype = sqltypes(i)
          sqltype match {
            case CHAR | VARCHAR =>
              stmt.setString(index, value.asInstanceOf[String]);
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
              if (value.isInstanceOf[ju.Date]) {
                if (value.isInstanceOf[Date]) stmt.setDate(index, value.asInstanceOf[Date])
                else stmt.setDate(index, new java.sql.Date(value.asInstanceOf[ju.Date].getTime()))
              } else if (value.isInstanceOf[ju.Calendar]) {
                val cal = value.asInstanceOf[ju.Calendar]
                stmt.setDate(index, new java.sql.Date(cal.getTime().getTime()), cal);
              } else {
                stmt.setObject(index, value, DATE);
              }
            }
            case TIME => {
              if (value.isInstanceOf[ju.Date]) {
                if (value.isInstanceOf[Time]) stmt.setTime(index, value.asInstanceOf[Time])
                else stmt.setTime(index, new java.sql.Time(value.asInstanceOf[ju.Date].getTime()))
              } else if (value.isInstanceOf[ju.Calendar]) {
                val cal = value.asInstanceOf[ju.Calendar]
                stmt.setTime(index, new Time(cal.getTime().getTime()), cal)
              } else {
                stmt.setObject(index, value, TIME);
              }
            }
            case TIMESTAMP => {
              if (value.isInstanceOf[ju.Date]) {
                if (value.isInstanceOf[Timestamp])
                  stmt.setTimestamp(index, value.asInstanceOf[Timestamp])
                else
                  stmt.setTimestamp(index, new Timestamp(value.asInstanceOf[ju.Date].getTime()))
              } else if (value.isInstanceOf[ju.Calendar]) {
                val cal = value.asInstanceOf[ju.Calendar]
                stmt.setTimestamp(index, new Timestamp(cal.getTime().getTime()), cal)
              } else {
                stmt.setObject(index, value, TIMESTAMP);
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
              if (isStringType(value.getClass)) stmt.setString(index, value.toString())
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
      i += 1
    }
  }
  protected def rethrow(cause: SQLException, sql: String, params: Any*) {
    var causeMessage = cause.getMessage()
    if (causeMessage == null) causeMessage = ""
    val msg = new StringBuffer(causeMessage)

    msg.append(" Query: ").append(sql).append(" Parameters: ")
    if (params == null) msg.append("[]")
    else msg.append(Strings.join(params, ","))

    val e = new SQLException(msg.toString(), cause.getSQLState(), cause.getErrorCode())
    e.setNextException(cause)
    throw e
  }

  private def convertToSeq(rs: ResultSet): Seq[Array[Any]] = {
    val meta = rs.getMetaData()
    val cols = meta.getColumnCount()
    val rows = new collection.mutable.ListBuffer[Array[Any]]
    val start = if (meta.getColumnName(1) == "_row_nr_") 1 else 0
    val rowlength = cols - start
    while (rs.next()) {
      val row = new Array[Any](rowlength)
      for (i <- start until cols) {
        var v = rs.getObject(i + 1)
        if (null != v && meta.getColumnType(i + 1) == TIMESTAMP && !v.isInstanceOf[Timestamp]) {
          if (null != JdbcExecutor.oracleTimestampMethod) v = JdbcExecutor.oracleTimestampMethod.invoke(v)
          else throw new Exception("Cannot translate " + v.getClass + "timestamp to java.sql.Timestamp")
        }
        row(i - start) = v
      }
      rows += row
    }
    rs.close()
    rows
  }
}
