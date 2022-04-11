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

package org.beangle.data.orm.hibernate.spring

import org.beangle.commons.logging.Logging
import org.hibernate.{FlushMode, Session}
import org.hibernate.context.internal.JTASessionContext
import org.hibernate.context.spi.CurrentSessionContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.springframework.transaction.support.TransactionSynchronizationManager._

/**
 * @author chaostone
 */
class BeangleSessionContext(val sessionFactory: SessionFactoryImplementor) extends CurrentSessionContext with Logging {

  /**
   * Retrieve the Spring-managed Session for the current thread, if any.
   */
  def currentSession: Session = {
    val sessionHolder = SessionUtils.currentSession(this.sessionFactory)

    val session = sessionHolder.session
    // TODO what time enter into the code?
    if (isSynchronizationActive && !sessionHolder.isSynchronizedWithTransaction) {
      registerSynchronization(new SessionSynchronization(sessionHolder, this.sessionFactory))
      sessionHolder.setSynchronizedWithTransaction(true)
      // Switch to FlushMode.AUTO, as we have to assume a thread-bound Session
      // with FlushMode.MANUAL, which needs to allow flushing within the transaction.
      val flushMode = session.getHibernateFlushMode
      if (FlushMode.MANUAL == flushMode && !isCurrentTransactionReadOnly) {
        session.setHibernateFlushMode(FlushMode.AUTO)
        sessionHolder.previousFlushMode = flushMode
      }
    }
    session
  }
}

class BeangleJtaSessionContext(factory: SessionFactoryImplementor) extends JTASessionContext(factory) {

  protected override def buildOrObtainSession(): Session = {
    val session = super.buildOrObtainSession()
    if (isCurrentTransactionReadOnly) {
      session.setHibernateFlushMode(FlushMode.MANUAL)
    }
    session
  }

}
