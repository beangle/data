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
import org.springframework.transaction.support.SmartTransactionObject

/**
 * JPA transaction object, representing a EntityManagerHolder.
 * Used as transaction object by JpaTransactionManager.
 */
class JpaTransactionObject(private var holder: SessionHolder = null, private var newSession: Boolean = false)
  extends SmartTransactionObject {
  var connectionHolder: ConnectionHolder = null
  var previousIsolationLevel: Option[Int] = None
  var readOnly = false
  var needsConnectionReset = false

  def setSession(session: SessionImplementor): SessionHolder = {
    this.holder = new SessionHolder(session)
    this.newSession = true
    this.holder
  }

  def isNewSession: Boolean = this.newSession

  def sessionHolder: SessionHolder = this.holder

  def setSessionHolder(sessionHolder: SessionHolder): Unit = {
    this.holder = sessionHolder
    this.newSession = false
  }

  def connectionPrepared(): Unit = {
    this.needsConnectionReset = true
  }

  def hasConnectionHolder: Boolean = this.connectionHolder != null

  def hasSessionHolder: Boolean = this.holder != null

  def hasTransaction: Boolean = this.holder != null && this.holder.transaction != null

  def session: SessionImplementor = holder.session

  def setRollbackOnly(): Unit = {
    holder.setRollbackOnly()
    if (hasConnectionHolder) connectionHolder.setRollbackOnly()
  }

  override def isRollbackOnly: Boolean = {
    holder.isRollbackOnly || (hasConnectionHolder && connectionHolder.isRollbackOnly)
  }

  override def flush(): Unit = session.flush()
}
