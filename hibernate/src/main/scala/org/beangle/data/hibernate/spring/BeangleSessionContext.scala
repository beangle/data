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
package org.beangle.data.hibernate.spring

import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.context.spi.CurrentSessionContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.springframework.core.Ordered
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
/**
 * @author chaostone
 */
class BeangleSessionContext(val sessionFactory: SessionFactoryImplementor) extends CurrentSessionContext {

  /**
   * Retrieve the Spring-managed Session for the current thread, if any.
   */
  def currentSession: Session = {
    val sessionHolder = SessionUtils.currentSession(this.sessionFactory)
    val session = sessionHolder.session
    // TODO what time enter into the code?
    if (TransactionSynchronizationManager.isSynchronizationActive()
      && !sessionHolder.isSynchronizedWithTransaction()) {
      TransactionSynchronizationManager.registerSynchronization(new SessionSynchronization(sessionHolder,
        this.sessionFactory))
      sessionHolder.setSynchronizedWithTransaction(true)
      // Switch to FlushMode.AUTO, as we have to assume a thread-bound Session
      // with FlushMode.MANUAL, which needs to allow flushing within the transaction.
      val flushMode = session.getFlushMode()
      if (FlushMode.MANUAL == flushMode && !TransactionSynchronizationManager.isCurrentTransactionReadOnly) {
        session.setFlushMode(FlushMode.AUTO)
        sessionHolder.previousFlushMode = flushMode
      }
    }
    session
  }
}

/**
 * Borrow from Spring Session Synchronization
 *
 * @author chaostone
 */
class SessionSynchronization(val sessionHolder: SessionHolder, val sessionFactory: SessionFactory) extends TransactionSynchronization with Ordered {

  var holderActive = true

  private def currentSession: Session = this.sessionHolder.session

  def getOrder(): Int = 1000 - 100

  def suspend() {
    if (this.holderActive) {
      TransactionSynchronizationManager.unbindResource(this.sessionFactory)
      // Eagerly disconnect the Session here, to make release mode "on_close" work on JBoss.
      currentSession.disconnect()
    }
  }

  def resume() {
    if (this.holderActive) TransactionSynchronizationManager.bindResource(this.sessionFactory, this.sessionHolder)
  }

  def flush() {
    currentSession.flush()
  }

  def beforeCommit(readOnly: Boolean) {
    if (!readOnly) {
      val session = currentSession
      // Read-write transaction -> flush the Hibernate Session.
      // Further check: only flush when not FlushMode.MANUAL.
      if (FlushMode.MANUAL != session.getFlushMode) session.flush()
    }
  }

  def beforeCompletion() {
    val session = this.sessionHolder.session
    if (this.sessionHolder.previousFlushMode != null) {
      // In case of pre-bound Session, restore previous flush mode.
      session.setFlushMode(this.sessionHolder.previousFlushMode)
    }
    // Eagerly disconnect the Session here, to make release mode "on_close" work nicely.
    session.disconnect()
  }

  def afterCommit() {
  }

  def afterCompletion(status: Int) {
    try {
      if (status != TransactionSynchronization.STATUS_COMMITTED) {
        // Clear all pending inserts/updates/deletes in the Session.
        // Necessary for pre-bound Sessions, to avoid inconsistent state.
        this.sessionHolder.session.clear()
      }
    } finally {
      this.sessionHolder.setSynchronizedWithTransaction(false)
    }
  }

}
