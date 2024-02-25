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

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.IOs
import org.beangle.data.jdbc.engine.Engine

import java.io.Closeable
import java.sql.ResultSet

class ResultSetIterator(rs: ResultSet, engine: Engine) extends Iterator[Array[Any]] with Closeable {

  var nextRecord: Array[Any] = _

  val types: Array[Int] = JdbcExecutor.getColumnTypes(rs, engine)

  val columnNames: Array[String] = JdbcExecutor.getColumnNames(rs)

  val columnDisplaySizes: Array[Int] = JdbcExecutor.getColumnDisplaySizes(rs)

  readNext()

  private def readNext(): Unit = {
    if (rs.next()) {
      nextRecord = JdbcExecutor.convert(rs, types)
    } else {
      nextRecord = null
      close()
    }
  }

  override def hasNext: Boolean = {
    nextRecord != null
  }

  override def next(): Array[Any] = {
    val previous = nextRecord
    readNext()
    previous
  }

  @throws[Exception]
  override def close(): Unit = {
    try {
      val smt = rs.getStatement
      if null == smt then IOs.close(rs) else IOs.close(rs, smt, smt.getConnection)
    } finally {
    }
  }

  def listAll(): collection.Seq[Array[Any]] = {
    val buf = Collections.newBuffer[Array[Any]]
    try {
      while (this.hasNext) {
        buf += this.next()
      }
      buf
    } finally {
      this.close()
    }
  }

}
