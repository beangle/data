/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
import org.springframework.transaction.support.TransactionSynchronizationManager._
import org.hibernate.context.internal.JTASessionContext
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform
import javax.transaction.TransactionManager
import org.beangle.commons.logging.Logging

/**
 * Borrow from Spring Session Synchronization
 *
 * @author chaostone
 */
class SessionSynchronization(val sessionHolder: SessionHolder, val sessionFactory: SessionFactory, val newSession: Boolean = false)
    extends TransactionSynchronization with Ordered {

  var holderActive = true

  private def currentSession: Session = this.sessionHolder.session

  def getOrder(): Int = 1000 - 100

  def suspend() {
    if (this.holderActive) {
      unbindResource(this.sessionFactory)
      // Eagerly disconnect the Session here, to make release mode "on_close" work on JBoss.
      currentSession.disconnect()
    }
  }

  def resume() {
    if (this.holderActive) bindResource(this.sessionFactory, this.sessionHolder)
  }

  def flush() {
    currentSession.flush()
  }

  def beforeCommit(readOnly: Boolean) {
    if (!readOnly) {
      val session = currentSession
      if (FlushMode.MANUAL != session.getHibernateFlushMode) session.flush()
    }
  }

  def beforeCompletion() {
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

  def afterCommit() {
  }

  def afterCompletion(status: Int) {
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
