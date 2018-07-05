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

import java.io.StringWriter
import java.lang.reflect.Method
import java.sql.{ BatchUpdateException, Connection, PreparedStatement, ResultSet, SQLException, Timestamp }
import java.sql.Types.TIMESTAMP

import org.beangle.commons.lang.{ ClassLoaders, Strings }
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.DefaultSqlTypeMapping
import org.beangle.data.jdbc.meta.Engines

import javax.sql.DataSource

object JdbcExecutor {
  var oracleTimestampMethod: Method = _
  try {
    val clz = ClassLoaders.load("oracle.sql.TIMESTAMP")
    oracleTimestampMethod = clz.getMethod("timestampValue")
  } catch {
    case e: Exception =>
  }
}

class JdbcExecutor(dataSource: DataSource) extends Logging {

  import JdbcExecutor._
  val engine = Engines.forDataSource(dataSource)
  val sqlTypeMapping = new DefaultSqlTypeMapping(engine)
  var showSql = false

  def unique[T](sql: String, params: Any*): Option[T] = {
    val rs = query(sql, params: _*)
    if (rs.isEmpty) None
    else Some(rs.head.head.asInstanceOf[T])
  }

  def queryForInt(sql: String): Option[Int] = {
    val num: Option[Number] = unique(sql)
    num match {
      case Some(n) => Some(n.intValue)
      case None    => None
    }
  }

  def queryForLong(sql: String): Option[Long] = {
    val num: Option[Number] = unique(sql)
    num match {
      case Some(n) => Some(n.longValue)
      case None    => None
    }
  }

  def getConnection(): Connection = dataSource.getConnection()

  def statement(sql: String): Statement = {
    new Statement(sql, this)
  }

  def query(sql: String, params: Any*): Seq[Array[Any]] = {
    query(sql, TypeParamSetter(sqlTypeMapping, params))
  }

  def query(sql: String, setter: PreparedStatement => Unit): Seq[Array[Any]] = {
    if (showSql) println("JdbcExecutor:" + sql)
    val conn = getConnection()
    var stmt: PreparedStatement = null
    var rs: ResultSet = null
    try {
      stmt = conn.prepareStatement(sql)
      setter(stmt)
      rs = stmt.executeQuery()
      convertToSeq(rs)
    } catch {
      case e: SQLException => rethrow(e, sql); List.empty
    } finally {
      if (null != rs) rs.close()
      if (null != stmt) stmt.close()
      conn.close()
    }
  }

  def update(sql: String, params: Any*): Int = {
    update(sql, TypeParamSetter(sqlTypeMapping, params))
  }

  def update(sql: String, setter: PreparedStatement => Unit): Int = {
    if (showSql) println("JdbcExecutor:" + sql)
    var stmt: PreparedStatement = null
    val conn = getConnection()
    if (conn.getAutoCommit()) conn.setAutoCommit(false)
    var rows = 0
    try {
      stmt = conn.prepareStatement(sql)
      setter(stmt)
      rows = stmt.executeUpdate()
      stmt.close()
      stmt = null
      conn.commit()
    } catch {
      case e: SQLException => {
        conn.rollback()
        rethrow(e, sql)
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
        ParamSetter.setParams(stmt, param, types)
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
