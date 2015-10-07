/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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

import org.beangle.commons.lang.annotation.description
import org.hibernate.{ ConnectionReleaseMode, FlushMode, HibernateException, Session, SessionFactory, Transaction }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.engine.transaction.spi.TransactionContext
import org.springframework.jdbc.datasource.{ ConnectionHolder, DataSourceUtils, JdbcTransactionObjectSupport }
import org.springframework.transaction.{ CannotCreateTransactionException, IllegalTransactionStateException, InvalidIsolationLevelException, TransactionDefinition, TransactionSystemException }
import org.springframework.transaction.support.{ AbstractPlatformTransactionManager, DefaultTransactionStatus, ResourceTransactionManager }
import org.springframework.transaction.support.TransactionSynchronizationManager.{ bindResource, getResource, hasResource, unbindResource }
import javax.sql.DataSource
/**
 * Simplify HibernateTransactionManager in spring-orm bundle.
 * Just add SessionUtils.isEnableThreadBinding() support in doGetTranscation
 *
 * @author chaostone
 */
@description("Beangle提供的Hibernate事务管理器")
class HibernateTransactionManager(val sessionFactory: SessionFactory) extends AbstractPlatformTransactionManager with ResourceTransactionManager {

  val dataSource: DataSource = SessionUtils.getDataSource(sessionFactory)

  def getResourceFactory(): AnyRef = sessionFactory

  protected override def doGetTransaction(): AnyRef = {
    val txObject = new HibernateTransactionObject()
    txObject.setSavepointAllowed(isNestedTransactionAllowed())

    val sessionHolder = getResource(sessionFactory).asInstanceOf[SessionHolder]
    if (sessionHolder != null) {
      txObject.setSessionHolder(sessionHolder)
    } else {
      if (SessionUtils.isEnableBinding(sessionFactory)) txObject.setSessionHolder(SessionUtils.openSession(sessionFactory))
    }
    txObject.setConnectionHolder(getResource(dataSource).asInstanceOf[ConnectionHolder])
    txObject
  }

  protected override def isExistingTransaction(transaction: AnyRef): Boolean = {
    transaction.asInstanceOf[HibernateTransactionObject].hasTransaction
  }

  protected override def doBegin(transaction: AnyRef, definition: TransactionDefinition) {
    val txObject = transaction.asInstanceOf[HibernateTransactionObject]

    if (txObject.hasConnectionHolder && !txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
      throw new IllegalTransactionStateException("Pre-bound JDBC Connection found! HibernateTransactionManager does not support.")
    }

    var session: Session = null
    try {
      if (txObject.sessionHolder == null || txObject.sessionHolder.isSynchronizedWithTransaction()) {
        txObject.setSession(sessionFactory.openSession())
      }
      session = txObject.sessionHolder.session
      if (isSameConnectionForEntireSession(session)) {
        val con = session.asInstanceOf[SessionImplementor].connection()
        val previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition)
        txObject.setPreviousIsolationLevel(previousIsolationLevel)
      } else {
        if (definition.getIsolationLevel != TransactionDefinition.ISOLATION_DEFAULT)
          throw new InvalidIsolationLevelException("HibernateTransactionManager is not allowed to support custom isolation levels.")
      }
      // Just set to NEVER in case of a new Session for this transaction.
      if (definition.isReadOnly() && txObject.isNewSession) session.setFlushMode(FlushMode.MANUAL)
      if (!definition.isReadOnly() && !txObject.isNewSession) {
        val flushMode = session.getFlushMode()
        if (session.getFlushMode == FlushMode.MANUAL) {
          session.setFlushMode(FlushMode.AUTO)
          txObject.sessionHolder.previousFlushMode = flushMode
        }
      }
      // Register transaction timeout.
      var hibTx: Transaction = null
      val timeout = determineTimeout(definition)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
        hibTx = session.getTransaction()
        hibTx.setTimeout(timeout)
        hibTx.begin()
      } else {
        hibTx = session.beginTransaction()
      }
      txObject.sessionHolder.transaction = hibTx
      // Register the Hibernate Session's JDBC Connection for the DataSource, if set.
      val con = session.asInstanceOf[SessionImplementor].connection()
      val conHolder = new ConnectionHolder(con)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) conHolder.setTimeoutInSeconds(timeout)

      bindResource(dataSource, conHolder)
      txObject.setConnectionHolder(conHolder)
      if (txObject.isNewSession) bindResource(sessionFactory, txObject.sessionHolder)
      txObject.sessionHolder.setSynchronizedWithTransaction(true)
    } catch {
      case ex: Exception =>
        if (txObject.isNewSession) {
          try {
            if (session.getTransaction.isActive()) session.getTransaction.rollback()
          } catch {
            case ex2: Throwable => logger.debug("Could not rollback Session after failed transaction begin", ex)
          } finally {
            SessionUtils.closeSession(session)
          }
        }
        throw new CannotCreateTransactionException("Could not open Hibernate Session for transaction", ex)
    }
  }

  protected override def doSuspend(transaction: AnyRef): AnyRef = {
    val txObject = transaction.asInstanceOf[HibernateTransactionObject]
    txObject.setSessionHolder(null)
    val sessionHolder = unbindResource(sessionFactory).asInstanceOf[SessionHolder]
    txObject.setConnectionHolder(null)
    var connectionHolder: ConnectionHolder = null
    connectionHolder = unbindResource(dataSource).asInstanceOf[ConnectionHolder]
    new SuspendedResourcesHolder(sessionHolder, connectionHolder)
  }

  protected override def doResume(transaction: AnyRef, suspendedResources: AnyRef) {
    val resourcesHolder = suspendedResources.asInstanceOf[SuspendedResourcesHolder]
    if (hasResource(sessionFactory)) unbindResource(sessionFactory)
    bindResource(sessionFactory, resourcesHolder.sessionHolder)
    bindResource(dataSource, resourcesHolder.connectionHolder)
  }

  protected override def doCommit(status: DefaultTransactionStatus) {
    val txObject = status.getTransaction().asInstanceOf[HibernateTransactionObject]
    try {
      txObject.sessionHolder.transaction.commit()
    } catch {
      case ex: org.hibernate.TransactionException => throw new TransactionSystemException("Could not commit transaction", ex)
      case ex2: HibernateException => throw ex2
    }
  }

  protected override def doRollback(status: DefaultTransactionStatus) {
    val txObject = status.getTransaction().asInstanceOf[HibernateTransactionObject]
    try {
      txObject.sessionHolder.transaction.rollback()
    } catch {
      case ex: org.hibernate.TransactionException => throw new TransactionSystemException("Could not roll back transaction", ex)
      case ex2: HibernateException => throw ex2
    } finally {
      if (!txObject.isNewSession) txObject.sessionHolder.session.clear()
    }
  }

  protected override def doSetRollbackOnly(status: DefaultTransactionStatus) {
    status.getTransaction().asInstanceOf[HibernateTransactionObject].setRollbackOnly()
  }

  protected override def doCleanupAfterCompletion(transaction: Object) {
    val txObject = transaction.asInstanceOf[HibernateTransactionObject]
    if (txObject.isNewSession) unbindResource(sessionFactory)
    unbindResource(dataSource)

    val holder = txObject.sessionHolder
    val session = holder.session
    if (session.isConnected() && isSameConnectionForEntireSession(session)) {
      try {
        val con = session.asInstanceOf[SessionImplementor].connection()
        DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel())
      } catch {
        case ex: HibernateException => logger.debug("Could not access JDBC Connection of Hibernate Session", ex)
      }
    }

    if (txObject.isNewSession) {
      SessionUtils.closeSession(session)
    } else {
      if (holder.previousFlushMode != null) session.setFlushMode(holder.previousFlushMode)
      session.disconnect()
    }
    holder.clear()
  }

  protected def isSameConnectionForEntireSession(session: Session): Boolean = session match {
    case tc: TransactionContext => ConnectionReleaseMode.ON_CLOSE == tc.getConnectionReleaseMode
    case _ => true
  }

  /**
   * Hibernate transaction object, representing a SessionHolder.
   * Used as transaction object by HibernateTransactionManager.
   */
  private class HibernateTransactionObject extends JdbcTransactionObjectSupport {
    var sessionHolder: SessionHolder = _
    var isNewSession: Boolean = false

    def setSession(session: Session): Unit = {
      this.sessionHolder = new SessionHolder(session)
      this.isNewSession = true
    }

    def setSessionHolder(sessionHolder: SessionHolder): Unit = {
      this.sessionHolder = sessionHolder
      this.isNewSession = false
    }

    def hasTransaction: Boolean = {
      (this.sessionHolder != null && this.sessionHolder.transaction != null)
    }

    def setRollbackOnly(): Unit = {
      this.sessionHolder.setRollbackOnly()
      if (hasConnectionHolder) getConnectionHolder.setRollbackOnly()
    }

    def isRollbackOnly: Boolean = {
      this.sessionHolder.isRollbackOnly || (hasConnectionHolder && getConnectionHolder.isRollbackOnly)
    }

    override def flush(): Unit = {
      this.sessionHolder.session.flush()
    }
  }

  private class SuspendedResourcesHolder(val sessionHolder: SessionHolder, val connectionHolder: ConnectionHolder)
}