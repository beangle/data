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
package org.beangle.data.hibernate.spring

import org.hibernate.{FlushMode, Session, SessionFactory}
import org.springframework.core.Ordered
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager._

/**
  * Borrow from Spring Session Synchronization
  * @author chaostone
  */
class SessionSynchronization(val sessionHolder: SessionHolder, val sessionFactory: SessionFactory, val newSession: Boolean = false)
  extends TransactionSynchronization with Ordered {

  var holderActive = true

  private def currentSession: Session = this.sessionHolder.session

  override def getOrder(): Int = 1000 - 100

  override def suspend(): Unit = {
    if (this.holderActive) {
      unbindResource(this.sessionFactory)
      // Eagerly disconnect the Session here, to make release mode "on_close" work on JBoss.
      currentSession.disconnect()
    }
  }

  override def resume(): Unit = {
    if (this.holderActive) bindResource(this.sessionFactory, this.sessionHolder)
  }

  override def flush(): Unit = {
    currentSession.flush()
  }

  override def beforeCommit(readOnly: Boolean): Unit = {
    if (!readOnly) {
      val session = currentSession
      if (FlushMode.MANUAL != session.getHibernateFlushMode) session.flush()
    }
  }

  override def beforeCompletion(): Unit = {
    try {
      val session = this.sessionHolder.session
      if (this.sessionHolder.previousFlushMode != null) {
        session.setHibernateFlushMode(this.sessionHolder.previousFlushMode)
      }
      session.disconnect()
    } finally {
      if (this.newSession) {
        unbindResource(this.sessionFactory)
        this.holderActive = false
      }
    }
  }

  override def afterCommit(): Unit = {
  }

  override def afterCompletion(status: Int): Unit = {
    try {
      if (status != TransactionSynchronization.STATUS_COMMITTED) {
        this.sessionHolder.session.clear()
      }
    } finally {
      this.sessionHolder.setSynchronizedWithTransaction(false)
      if (this.newSession) {
        currentSession.close()
      }
    }
  }

}
