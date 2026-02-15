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

import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.{FlushMode, StatelessSession, Transaction}
import org.springframework.transaction.support.ResourceHolderSupport

/** 移植自spring orm的SessionHolder
 * 去掉transactionActive,savepointManager
 * 增加了statelessSession,transaction,previousFlushMode
 *
 * @param session session
 */
class SessionHolder(val session: SessionImplementor) extends ResourceHolderSupport {
  var statelessSession: StatelessSession = _
  var transaction: Transaction = _
  var previousFlushMode: FlushMode = _

  override def clear(): Unit = {
    super.clear()
    this.transaction = null
    this.previousFlushMode = null
  }

  def closeAll(): Unit = {
    try {
      if (this.session != null && this.session.isOpen) this.session.close()
      if (this.statelessSession != null && this.statelessSession.isOpen) this.statelessSession.close()
    } catch {
      case e: Exception =>
    }
  }
}
