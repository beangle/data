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

import org.beangle.data.hibernate.SessionHelper
import org.beangle.data.hibernate.tx.HibernateTransactionManager.*
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.{TransactionException as HibernateTransactionException, *}
import org.springframework.transaction.*
import org.springframework.transaction.support.{TransactionSynchronizationManager as Tsm, *}

import javax.sql.DataSource

/** 处理Hibernate中的事务
 *
 * - 禁用了hibernate实体拦截器(EntityInterceptor)
 * - Let hibernateManagedSession  false
 * - Disable exceptionTranslator
 * - Force prepareConnection = true
 * - 删除了检查点支持
 *
 * @param sessionFactory SessionFactory
 * @see spring-orm中的HibernateTransactionManager
 */
class HibernateTransactionManager(val sessionFactory: SessionFactory)
  extends AbstractPlatformTransactionManager, ResourceTransactionManager {

  var dataSource: DataSource = _

  override def getResourceFactory: Object = sessionFactory

  protected override def doGetTransaction(): Object = {
    val txObject = new JpaTransactionObject()

    var emHolder = Tsm.getResource(sessionFactory).asInstanceOf[SessionHolder]
    if (null == emHolder) {
      // Beangle Added. for avoid openSession in view explicitly.
      // Open a session in place,and the new emHolder will be bind in doBegin function.
      // and the temporal session will be close in doCleanupAfterCompletion
      emHolder = txObject.setSession(SessionHelper.doOpenSession(sessionFactory))
    } else {
      txObject.setSessionHolder(emHolder)
    }

    val con = Tsm.getResource(dataSource).asInstanceOf[ConnectionHolder]
    txObject.connectionHolder = con
    txObject
  }

  protected override def isExistingTransaction(transaction: Object): Boolean = {
    transaction.asInstanceOf[JpaTransactionObject].hasTransaction
  }

  protected override def doBegin(transaction: Object, definition: TransactionDefinition): Unit = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]

    if (txObject.hasConnectionHolder && !txObject.connectionHolder.isSynchronizedWithTransaction) {
      throw new IllegalTransactionStateException("Pre-bound JDBC Connection found!")
    }

    var em: SessionImplementor = null
    try {
      if (!txObject.hasSessionHolder || txObject.sessionHolder.isSynchronizedWithTransaction) {
        txObject.setSession(SessionHelper.doOpenSession(sessionFactory))
      }
      em = txObject.session.unwrap(classOf[SessionImplementor])

      val isolationLevelNeeded = definition.getIsolationLevel != TransactionDefinition.ISOLATION_DEFAULT
      if (isolationLevelNeeded || definition.isReadOnly) {
        val conn = em.getJdbcCoordinator.getLogicalConnection
        if (ConnectionReleaseMode.ON_CLOSE == conn.getConnectionHandlingMode.getReleaseMode) {
          val con = conn.getPhysicalConnection
          val previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition)
          txObject.previousIsolationLevel = previousIsolationLevel
          txObject.readOnly = definition.isReadOnly
          txObject.connectionPrepared()
        } else {
          if (isolationLevelNeeded) {
            throw new InvalidIsolationLevelException("HibernateTransactionManager is not allowed to support custom isolation levels.")
          }
        }
      }

      if (definition.isReadOnly && txObject.isNewSession) {
        em.setHibernateFlushMode(FlushMode.MANUAL)
        em.setDefaultReadOnly(true)
      }

      if (!definition.isReadOnly && !txObject.isNewSession) {
        val flushMode = em.getHibernateFlushMode
        if (FlushMode.MANUAL == flushMode) {
          em.setHibernateFlushMode(FlushMode.AUTO)
          txObject.sessionHolder.previousFlushMode = flushMode
        }
      }

      var hibTx: Transaction = null
      // Delegate to JpaDialect for actual transaction begin.
      val timeout = determineTimeout(definition)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
        hibTx = em.getTransaction
        hibTx.setTimeout(timeout)
        hibTx.begin()
      } else {
        hibTx = em.beginTransaction() // Open a plain Hibernate transaction without specified timeout.
      }
      // Add the Hibernate transaction to the session holder
      txObject.sessionHolder.transaction = hibTx

      // Register the Hibernate Session's JDBC Connection for the DataSource, if set.
      val conHolder = ConnectionHolder.getHolder(em)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
        conHolder.setTimeoutInSeconds(timeout)
      }
      Tsm.bindResource(dataSource, conHolder)
      txObject.connectionHolder = conHolder

      // Bind the entity manager holder to the thread.
      if (txObject.isNewSession) {
        Tsm.bindResource(sessionFactory, txObject.sessionHolder)
      }
      txObject.sessionHolder.setSynchronizedWithTransaction(true)
    } catch {
      case ex: Throwable =>
        if (txObject.isNewSession) {
          val em = txObject.session
          try
            if em.getTransaction.isActive then em.getTransaction.rollback()
          catch
            case ex: Throwable =>
          finally {
            SessionHelper.safeCloseSession(em)
            txObject.setSessionHolder(null)
          }
        }
        throw new CannotCreateTransactionException("Could not open session for transaction", ex)
    }
  }

  protected override def doSuspend(transaction: Object): Object = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]
    txObject.setSessionHolder(null)
    val sessionHolder = Tsm.unbindResource(sessionFactory).asInstanceOf[SessionHolder]
    txObject.connectionHolder = null
    val conHolder = Tsm.unbindResource(dataSource).asInstanceOf[ConnectionHolder]
    new SuspendedResourcesHolder(sessionHolder, conHolder)
  }

  protected override def doResume(transaction: Object, suspendedResources: Object): Unit = {
    val resourcesHolder = suspendedResources.asInstanceOf[SuspendedResourcesHolder]
    if Tsm.hasResource(sessionFactory) then Tsm.unbindResource(sessionFactory)

    Tsm.bindResource(sessionFactory, resourcesHolder.sessionHolder)
    if (resourcesHolder.connectionHolder != null) {
      Tsm.bindResource(dataSource, resourcesHolder.connectionHolder)
    }
  }

  protected override def doCommit(status: DefaultTransactionStatus): Unit = {
    val txObject = status.getTransaction.asInstanceOf[JpaTransactionObject]
    try {
      txObject.sessionHolder.transaction.commit()
    } catch {
      case ex: HibernateTransactionException => throw new TransactionSystemException("Could not commit transaction", ex)
      case ex: RuntimeException => throw ex
    }
  }

  protected override def doRollback(status: DefaultTransactionStatus): Unit = {
    val txObject = status.getTransaction.asInstanceOf[JpaTransactionObject]
    val tx = txObject.sessionHolder.transaction
    try {
      tx.rollback()
    } catch {
      case pe: HibernateTransactionException => throw new TransactionSystemException("Could not roll back transaction", pe)
    } finally {
      if !txObject.isNewSession then txObject.session.clear()
    }
  }

  protected override def doSetRollbackOnly(status: DefaultTransactionStatus): Unit = {
    status.getTransaction.asInstanceOf[JpaTransactionObject].setRollbackOnly()
  }

  protected override def doCleanupAfterCompletion(transaction: Object): Unit = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]
    // Remove the JDBC connection holder from the thread, if exposed.
    Tsm.unbindResource(dataSource)

    val session = txObject.sessionHolder.session.unwrap(classOf[SessionImplementor])
    val lconn = session.getJdbcCoordinator.getLogicalConnection
    if (txObject.needsConnectionReset && lconn.isPhysicallyConnected) {
      DataSourceUtils.resetConnectionAfterTransaction(lconn.getPhysicalConnection, txObject.previousIsolationLevel, txObject.readOnly)
    }

    if (txObject.isNewSession) {
      Tsm.unbindResourceIfPossible(sessionFactory)
      txObject.sessionHolder.closeAll()
    } else {
      if (txObject.sessionHolder.previousFlushMode != null) {
        session.setHibernateFlushMode(txObject.sessionHolder.previousFlushMode)
      }
      lconn.manualDisconnect()
    }
    txObject.sessionHolder.clear()
  }
}

object HibernateTransactionManager {

  private class SuspendedResourcesHolder(val sessionHolder: SessionHolder, val connectionHolder: ConnectionHolder)
}
