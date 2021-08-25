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

package org.beangle.data.hibernate.spring

import java.util.function.Consumer

import javax.sql.DataSource
import org.beangle.commons.lang.annotation.description
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate._
import org.springframework.jdbc.datasource.{ConnectionHolder, DataSourceUtils, JdbcTransactionObjectSupport}
import org.springframework.transaction.support.TransactionSynchronizationManager.{bindResource, getResource, hasResource, unbindResource}
import org.springframework.transaction.support.{AbstractPlatformTransactionManager, DefaultTransactionStatus, ResourceTransactionManager}
import org.springframework.transaction._

/**
 * Simplify HibernateTransactionManager in spring-orm bundle.
 * Just add SessionUtils.isEnableThreadBinding() support in doGetTranscation
 * <ul>
 *   <li> disable hibernateManagedSession.
 *   <li> enable connectionPrepared
 *   <li> disable holdabilityNeeded
 * </ul>
 * @author chaostone
 */
@description("Beangle提供的Hibernate事务管理器")
class HibernateTransactionManager(val sessionFactory: SessionFactory) extends AbstractPlatformTransactionManager with ResourceTransactionManager {

  val dataSource: DataSource = SessionUtils.getDataSource(sessionFactory)
  var entityInterceptor:Option[Interceptor] = None
  var sessionInitializer:Option[Consumer[Session]] = None

  def getResourceFactory: AnyRef = sessionFactory

  protected override def doGetTransaction(): AnyRef = {
    val txObject = new HibernateTransactionObject()
    txObject.setSavepointAllowed(isNestedTransactionAllowed)

    val sessionHolder = getResource(sessionFactory).asInstanceOf[SessionHolder]
    if (sessionHolder != null) {
      txObject.setSessionHolder(sessionHolder)
    } else {
      // beangle add
      if (SessionUtils.isEnableBinding(sessionFactory)) {
        txObject.setSessionHolder(SessionUtils.openSession(sessionFactory))
      }
    }
    txObject.setConnectionHolder(getResource(dataSource).asInstanceOf[ConnectionHolder])
    txObject
  }

  protected override def isExistingTransaction(transaction: AnyRef): Boolean = {
    transaction.asInstanceOf[HibernateTransactionObject].hasTransaction
  }

  protected override def doBegin(transaction: AnyRef, definition: TransactionDefinition): Unit = {
    val txObject = transaction.asInstanceOf[HibernateTransactionObject]

    if (txObject.hasConnectionHolder && !txObject.getConnectionHolder.isSynchronizedWithTransaction) {
      throw new IllegalTransactionStateException("Pre-bound JDBC Connection found! HibernateTransactionManager does not support.")
    }

    var session: SessionImplementor = null
    try {
      if (txObject.sessionHolder == null || txObject.sessionHolder.isSynchronizedWithTransaction) {
        txObject.setSession(SessionUtils.doOpenSession(sessionFactory,entityInterceptor,sessionInitializer))
      }
      session = txObject.sessionHolder.session.unwrap(classOf[SessionImplementor])
      val isolationLevelNeeded = definition.getIsolationLevel != TransactionDefinition.ISOLATION_DEFAULT
      if (isolationLevelNeeded || definition.isReadOnly) {
        if (ConnectionReleaseMode.ON_CLOSE.equals(session.getJdbcCoordinator.getLogicalConnection.getConnectionHandlingMode.getReleaseMode)) {
          val con = session.connection()
          val previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition)
          txObject.setPreviousIsolationLevel(previousIsolationLevel)
          txObject.setReadOnly(definition.isReadOnly)
        } else {
          if (isolationLevelNeeded)
            throw new InvalidIsolationLevelException("HibernateTransactionManager is not allowed to support custom isolation levels.")
        }
      }
      // Just set to NEVER in case of a new Session for this transaction.
      if (definition.isReadOnly && txObject.isNewSession) {
        session.setHibernateFlushMode(FlushMode.MANUAL)
        session.setDefaultReadOnly(true)
      }
      if (!definition.isReadOnly && !txObject.isNewSession) {
        val flushMode = session.getHibernateFlushMode
        if (session.getHibernateFlushMode == FlushMode.MANUAL) {
          session.setHibernateFlushMode(FlushMode.AUTO)
          txObject.sessionHolder.previousFlushMode = flushMode
        }
      }
      // Register transaction timeout.
      var hibTx: Transaction = null
      val timeout = determineTimeout(definition)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
        hibTx = session.asInstanceOf[SharedSessionContract].getTransaction
        hibTx.setTimeout(timeout)
        hibTx.begin()
      } else {
        hibTx = session.beginTransaction()
      }
      txObject.sessionHolder.transaction = hibTx
      // Register the Hibernate Session's JDBC Connection for the DataSource, if set.
      val con = session.connection()
      val conHolder = new ConnectionHolder(con)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) conHolder.setTimeoutInSeconds(timeout)

      bindResource(dataSource, conHolder)
      txObject.setConnectionHolder(conHolder)
      if (txObject.isNewSession) bindResource(sessionFactory, txObject.sessionHolder)
      txObject.sessionHolder.setSynchronizedWithTransaction(true)
    } catch {
      case ex: Throwable =>
        if (txObject.isNewSession) {
          try {
            if (null!=session && session.getTransaction.isActive) session.getTransaction.rollback()
          } catch {
            case _: Throwable => logger.debug("Could not rollback Session after failed transaction begin", ex)
          } finally {
            SessionUtils.closeSession(session)
            txObject.setSessionHolder(null)
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
    val connectionHolder = unbindResource(dataSource).asInstanceOf[ConnectionHolder]
    new SuspendedResourcesHolder(sessionHolder, connectionHolder)
  }

  protected override def doResume(transaction: AnyRef, suspendedResources: AnyRef): Unit = {
    val resourcesHolder = suspendedResources.asInstanceOf[SuspendedResourcesHolder]
    if (hasResource(sessionFactory)) unbindResource(sessionFactory)
    bindResource(sessionFactory, resourcesHolder.sessionHolder)
    bindResource(dataSource, resourcesHolder.connectionHolder)
  }

  protected override def doCommit(status: DefaultTransactionStatus): Unit = {
    val txObject = status.getTransaction.asInstanceOf[HibernateTransactionObject]
    try {
      txObject.sessionHolder.transaction.commit()
    } catch {
      case ex: org.hibernate.TransactionException => throw new TransactionSystemException("Could not commit transaction", ex)
      case ex2: HibernateException => throw ex2
      case ex3: Exception => throw new RuntimeException(ex3)
    }
  }

  protected override def doRollback(status: DefaultTransactionStatus): Unit = {
    val txObject = status.getTransaction.asInstanceOf[HibernateTransactionObject]
    try {
      txObject.sessionHolder.transaction.rollback()
    } catch {
      case ex: org.hibernate.TransactionException => throw new TransactionSystemException("Could not roll back transaction", ex)
      case ex2: HibernateException => throw ex2
    } finally {
      if (!txObject.isNewSession) txObject.sessionHolder.session.clear()
    }
  }

  protected override def doSetRollbackOnly(status: DefaultTransactionStatus): Unit = {
    status.getTransaction.asInstanceOf[HibernateTransactionObject].setRollbackOnly()
  }

  protected override def doCleanupAfterCompletion(transaction: Object): Unit = {
    val txObject = transaction.asInstanceOf[HibernateTransactionObject]
    if (txObject.isNewSession) unbindResource(sessionFactory)
    unbindResource(dataSource)

    val holder = txObject.sessionHolder
    val session = holder.session.unwrap(classOf[SessionImplementor])
    if (session.getJdbcCoordinator.getLogicalConnection.isPhysicallyConnected) {
      try {
        val con = session.connection()
        DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel, txObject.isReadOnly)
      } catch {
        case ex: HibernateException => logger.debug("Could not access JDBC Connection of Hibernate Session", ex)
      }
    }

    if (txObject.isNewSession) {
      SessionUtils.closeSession(session)
    } else {
      if (holder.previousFlushMode != null) session.setHibernateFlushMode(holder.previousFlushMode)
      session.disconnect()
    }
    holder.clear()
  }

  protected def isSameConnectionForEntireSession(session: Session): Boolean = {
    session match {
      case tc: SessionImplementor =>
        ConnectionReleaseMode.ON_CLOSE == tc.getJdbcSessionContext.getPhysicalConnectionHandlingMode.getReleaseMode;
      case _ => true
    }
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
      this.sessionHolder != null && this.sessionHolder.transaction != null
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
