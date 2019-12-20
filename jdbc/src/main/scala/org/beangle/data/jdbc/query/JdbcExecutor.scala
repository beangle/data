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

import java.sql.Types.{BLOB, CLOB, DATE, TIMESTAMP}
import java.sql.{BatchUpdateException, Connection, PreparedStatement, ResultSet, SQLException}

import javax.sql.DataSource
import org.beangle.commons.lang.Strings
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.DefaultSqlTypeMapping
import org.beangle.data.jdbc.engine.Engines

import scala.collection.immutable.ArraySeq

object JdbcExecutor {
  def convert(rs: ResultSet, types: Array[Int]): Array[Any] = {
    val objs = Array.ofDim[Any](types.length)
    types.indices foreach { i =>
      var v = rs.getObject(i + 1)
      if (null != v) {
        types(i) match {
          // timstamp 在驱动中类型会和java.sql.Timestamp不同
          case TIMESTAMP => v = rs.getTimestamp(i + 1)
          case DATE => v = rs.getDate(i + 1)
          case BLOB =>
            val blob = rs.getBlob(i + 1)
            v = blob.getBytes(1, blob.length.toInt)
          case CLOB =>
            val clob = rs.getClob(i + 1)
            v = clob.getSubString(1, clob.length.toInt)
          case _ =>
        }
      }
      objs(i) = v
    }
    objs
  }
}

class JdbcExecutor(dataSource: DataSource) extends Logging {

  private val engine = Engines.forDataSource(dataSource)
  val sqlTypeMapping = new DefaultSqlTypeMapping(engine)
  var showSql = false
  var fetchSize = 1000

  def unique[T](sql: String, params: Any*): Option[T] = {
    val rs = query(sql, params: _*)
    if (rs.isEmpty) {
      None
    } else {
      val o = rs.next()
      Some(o.head.asInstanceOf[T])
    }
  }

  def queryForInt(sql: String): Option[Int] = {
    val num: Option[Number] = unique(sql)
    num match {
      case Some(n) => Some(n.intValue)
      case None => None
    }
  }

  def queryForLong(sql: String): Option[Long] = {
    val num: Option[Number] = unique(sql)
    num match {
      case Some(n) => Some(n.longValue)
      case None => None
    }
  }

  def openConnection(): Connection = dataSource.getConnection()

  def statement(sql: String): Statement = {
    new Statement(sql, this)
  }

  def query(sql: String, params: Any*): Iterator[Array[Any]] = {
    query(sql, TypeParamSetter(sqlTypeMapping, params))
  }

  def query(sql: String, setter: PreparedStatement => Unit): Iterator[Array[Any]] = {
    if (showSql) println("JdbcExecutor:" + sql)
    val conn = openConnection()
    conn.setAutoCommit(false)
    val stmt = conn.prepareStatement(sql)
    stmt.setFetchSize(fetchSize)
    setter(stmt)
    val rs = stmt.executeQuery()
    new ResultSetIterator(rs)
  }

  def update(sql: String, params: Any*): Int = {
    update(sql, TypeParamSetter(sqlTypeMapping, params))
  }

  def update(sql: String, setter: PreparedStatement => Unit): Int = {
    if (showSql) println("JdbcExecutor:" + sql)
    var stmt: PreparedStatement = null
    val conn = openConnection()
    if (conn.getAutoCommit) conn.setAutoCommit(false)
    var rows = 0
    try {
      stmt = conn.prepareStatement(sql)
      setter(stmt)
      rows = stmt.executeUpdate()
      stmt.close()
      stmt = null
      conn.commit()
    } catch {
      case e: SQLException =>
        conn.rollback()
        rethrow(e, sql)
    } finally {
      if (null != stmt) stmt.close()
      conn.close()
    }
    rows
  }

  def batch(sql: String, datas: collection.Seq[Array[_]], types: collection.Seq[Int]): Seq[Int] = {
    if (showSql) println("JdbcExecutor:" + sql)
    var stmt: PreparedStatement = null
    val conn = openConnection()
    if (conn.getAutoCommit) conn.setAutoCommit(false)
    val rows = new collection.mutable.ListBuffer[Int]
    var curParam: Seq[_] = null
    try {
      stmt = conn.prepareStatement(sql)
      for (param <- datas) {
        curParam = ArraySeq.unsafeWrapArray(param)
        ParamSetter.setParams(stmt, param, types)
        stmt.addBatch()
      }
      rows ++= stmt.executeBatch()
      conn.commit()
    } catch {
      case be: BatchUpdateException =>
        conn.rollback()
        rethrow(be.getNextException, sql, curParam)
      case e: SQLException =>
        conn.rollback()
        rethrow(e, sql, curParam)
    } finally {
      stmt.close()
      conn.close()
    }
    rows.toList
  }

  protected def rethrow(cause: SQLException, sql: String, params: Any*): Unit = {
    var causeMessage = cause.getMessage
    if (causeMessage == null) causeMessage = ""
    val msg = new StringBuffer(causeMessage)

    msg.append(" Query: ").append(sql).append(" Parameters: ")
    if (params == null) msg.append("[]")
    else msg.append(Strings.join(params, ","))

    val e = new SQLException(msg.toString, cause.getSQLState, cause.getErrorCode)
    e.setNextException(cause)
    throw e
  }
}
