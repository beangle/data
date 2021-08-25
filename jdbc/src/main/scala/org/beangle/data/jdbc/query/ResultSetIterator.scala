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

import java.io.Closeable
import java.sql.ResultSet

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.IOs

class ResultSetIterator(rs: ResultSet) extends Iterator[Array[Any]] with Closeable {

  var nextRecord: Array[Any] = _

  private val types = getTypes(rs)

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

  private def getTypes(rs: ResultSet): Array[Int] = {
    val meta = rs.getMetaData
    val cols =
      if (meta.getColumnName(meta.getColumnCount) == "_rownum_") {
        meta.getColumnCount - 1
      } else {
        meta.getColumnCount
      }
    val typ = Array.ofDim[Int](cols)
    (0 until cols) foreach { i =>
      typ(i) = meta.getColumnType(i + 1)
    }
    typ
  }

  @throws[Exception]
  override def close(): Unit = {
    try {
      val smt = rs.getStatement
      val con = smt.getConnection
      IOs.close(rs, smt, con)
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
