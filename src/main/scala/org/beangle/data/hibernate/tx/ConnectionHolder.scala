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

import org.beangle.data.hibernate.tx.ConnectionHandle
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.transaction.support.ResourceHolderSupport

import java.sql.Connection

object ConnectionHolder {

  def getHolder(session: SessionImplementor): ConnectionHolder = {
    new ConnectionHolder(new HibernateConnectionHandle(session))
  }

  private class HibernateConnectionHandle(session: SessionImplementor) extends ConnectionHandle {
    override def get(): Connection = {
      session.getJdbcCoordinator.getLogicalConnection.getPhysicalConnection
    }
  }
}

/** 借鉴自spring-jdbc的ConnectionHolder
 *  - 删除了transactionActive，事务代码没有使用到
 *  - 删除检查点支持
 *
 * @param connectionHandle handler
 */
class ConnectionHolder(val connectionHandle: ConnectionHandle) extends ResourceHolderSupport {
  private var currentConnection: Connection = null

  protected def hasConnection: Boolean = this.connectionHandle != null

  /** 获得连接，缓存，并返回 */
  def getConnection: Connection = {
    if (this.currentConnection == null) this.currentConnection = this.connectionHandle.get()
    this.currentConnection
  }

  /** 释放连接 */
  override def released(): Unit = {
    super.released()
    if (!isOpen && this.currentConnection != null) {
      if (this.connectionHandle != null) this.connectionHandle.release(this.currentConnection)
      this.currentConnection = null
    }
  }

}
