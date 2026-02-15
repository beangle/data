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

package org.beangle.data.hibernate.tx

import org.beangle.data.Logger
import org.springframework.transaction.TransactionDefinition

import java.sql.Connection

object DataSourceUtils {

  def prepareConnectionForTransaction(con: Connection, dfn: TransactionDefinition): Option[Int] = {
    prepareConnectionForTransaction(con, if (dfn != null) dfn.getIsolationLevel
    else TransactionDefinition.ISOLATION_DEFAULT, dfn != null && dfn.isReadOnly)
  }

  private def prepareConnectionForTransaction(con: Connection, isolationLevel: Int, setReadOnly: Boolean): Option[Int] = {
    if (setReadOnly) {
      setReadOnlyIfPossible(con)
    }
    // Apply specific isolation level, if any.
    var previousIsolationLevel: Option[Int] = None
    if (isolationLevel != TransactionDefinition.ISOLATION_DEFAULT) {
      val currentIsolation = con.getTransactionIsolation
      if (currentIsolation != isolationLevel) {
        previousIsolationLevel = Some(currentIsolation)
        con.setTransactionIsolation(isolationLevel)
      }
    }
    previousIsolationLevel
  }

  def resetConnectionAfterTransaction(con: Connection, previousIsolationLevel: Option[Int], resetReadOnly: Boolean): Unit = {
    try {
      previousIsolationLevel foreach (i => con.setTransactionIsolation(i))
      if resetReadOnly then con.setReadOnly(false)
    } catch {
      case ex: Throwable => Logger.debug("重置失败", ex)
    }
  }

  /** 尽量设置为只读模式
   *
   * @param con
   */
  def setReadOnlyIfPossible(con: Connection): Unit = {
    try {
      con.setReadOnly(true)
    } catch {
      case ex: Throwable =>
        //有的数据库连接不支持只读模式，如果是超时就抛出来
        var exToCheck = ex
        while (exToCheck != null) {
          if (exToCheck.getClass.getSimpleName.contains("Timeout")) throw ex
          exToCheck = exToCheck.getCause
        }
    }
  }
}
