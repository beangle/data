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

package org.beangle.data.orm.hibernate

import jakarta.persistence.{EntityManager, EntityManagerFactory, PersistenceException, RollbackException}
import org.beangle.data.orm.hibernate.HibernateTransactionManager.*
import org.hibernate.{Session, SessionFactory}
import org.springframework.jdbc.datasource.{ConnectionHolder, JdbcTransactionObjectSupport}
import org.springframework.transaction.*
import org.springframework.transaction.support.*
import org.springframework.transaction.support.TransactionSynchronizationManager.{bindResource, getResource, hasResource, unbindResource}

import javax.sql.DataSource

object HibernateTransactionManager {
  class SessionHolder(val session: Session) extends ResourceHolderSupport {
    var savepointManager: SavepointManager = _
    var transactionActive: Boolean = _

    override def clear(): Unit = {
      super.clear()
      this.savepointManager = null
      this.transactionActive = false
    }

  }

  class SuspendedResourcesHolder(val sessionHolder: SessionHolder, val connectionHolder: ConnectionHolder)

  class JpaTransactionDefinition(targetDefinition: TransactionDefinition, val timeout: Int, val localResource: Boolean)
    extends DelegatingTransactionDefinition(targetDefinition), ResourceTransactionDefinition {
    override def isLocalResource: Boolean = localResource

    override def getTimeout: Int = timeout
  }

}

class HibernateTransactionManager(val sessionFactory: EntityManagerFactory)
  extends AbstractPlatformTransactionManager, ResourceTransactionManager {

  var dataSource: DataSource = _

  var jpaPropertyMap: java.util.Map[String, Object] = _

  setNestedTransactionAllowed(true)

  override def getResourceFactory: Object = sessionFactory

  protected override def doGetTransaction(): Object = {
    val txObject = new JpaTransactionObject()
    txObject.setSavepointAllowed(isNestedTransactionAllowed)

    val emHolder = TransactionSynchronizationManager.getResource(sessionFactory).asInstanceOf[SessionHolder]
    if (emHolder != null) {
      txObject.update(emHolder, false)
    } else {
      // BEANGLE ADD. for avoid openSession in view explicitly.
      txObject.sessionHolder = SessionHelper.openSession(sessionFactory.asInstanceOf[SessionFactory])
    }

    if (dataSource != null) {
      val conHolder = TransactionSynchronizationManager.getResource(dataSource).asInstanceOf[ConnectionHolder]
      txObject.setConnectionHolder(conHolder)
    }

    txObject
  }

  protected override def isExistingTransaction(transaction: Object): Boolean = {
    transaction.asInstanceOf[JpaTransactionObject].hasTransaction
  }

  protected override def doBegin(transaction: Object, definition: TransactionDefinition): Unit = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]

    if (txObject.hasConnectionHolder && !txObject.getConnectionHolder.isSynchronizedWithTransaction) {
      throw new IllegalTransactionStateException(
        "Pre-bound JDBC Connection found! JpaTransactionManager does not support " +
          "running within DataSourceTransactionManager if told to manage the DataSource itself. " +
          "It is recommended to use a single JpaTransactionManager for all transactions " +
          "on a single DataSource, no matter whether JPA or JDBC access.")
    }

    try {
      if (!txObject.hasSessionHolder ||
        txObject.sessionHolder.isSynchronizedWithTransaction) {
        val newEm = createEntityManagerForTransaction()
        txObject.update(new SessionHolder(newEm), true)
      }

      val em = txObject.session

      // Delegate to JpaDialect for actual transaction begin.
      val timeoutToUse = determineTimeout(definition)
      val transactionData = SessionHelper.beginTransaction(em, new JpaTransactionDefinition(definition, timeoutToUse, txObject.isNewSession))
      txObject.transactionData = transactionData
      txObject.setReadOnly(definition.isReadOnly)

      // Register transaction timeout.
      if (timeoutToUse != TransactionDefinition.TIMEOUT_DEFAULT) {
        txObject.sessionHolder.setTimeoutInSeconds(timeoutToUse)
      }

      // Register the JPA EntityManager's JDBC Connection for the DataSource, if set.
      if (dataSource != null) {
        val conHandle = SessionHelper.getJdbcConnection(em, definition.isReadOnly)
        if (conHandle != null) {
          val conHolder = new ConnectionHolder(conHandle)
          if (timeoutToUse != TransactionDefinition.TIMEOUT_DEFAULT) {
            conHolder.setTimeoutInSeconds(timeoutToUse)
          }
          TransactionSynchronizationManager.bindResource(dataSource, conHolder)
          txObject.setConnectionHolder(conHolder)
        }
      }

      // Bind the entity manager holder to the thread.
      if (txObject.isNewSession) {
        TransactionSynchronizationManager.bindResource(sessionFactory, txObject.sessionHolder)
      }
      txObject.sessionHolder.setSynchronizedWithTransaction(true)
    } catch {
      case ex: TransactionException =>
        closeEntityManagerAfterFailedBegin(txObject)
        throw ex
      case ex: Throwable =>
        closeEntityManagerAfterFailedBegin(txObject)
        throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction", ex)
    }
  }

  /**
   * Create a JPA EntityManager to be used for a transaction.
   */
  protected def createEntityManagerForTransaction(): Session = {
    val emf = sessionFactory
    val properties = jpaPropertyMap
    val em = if null == properties || properties.isEmpty then emf.createEntityManager() else emf.createEntityManager(properties)
    em.asInstanceOf[Session]
  }

  /**
   * Close the current transaction's EntityManager.
   * Called after a transaction begin attempt failed.
   *
   * @param txObject the current transaction
   */
  protected def closeEntityManagerAfterFailedBegin(txObject: JpaTransactionObject): Unit = {
    if (txObject.isNewSession) {
      val em = txObject.session
      try
        if em.getTransaction.isActive then em.getTransaction.rollback()
      catch
        case ex: Throwable =>
      finally
        SessionHelper.safeCloseSession(em)

      txObject.update(null, false)
    }
  }

  protected override def doSuspend(transaction: Object): Object = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]
    txObject.update(null, false)
    val sessionHolder = TransactionSynchronizationManager.unbindResource(sessionFactory).asInstanceOf[SessionHolder]
    txObject.setConnectionHolder(null)
    var connectionHolder: ConnectionHolder = null
    val ds = dataSource
    if (ds != null && TransactionSynchronizationManager.hasResource(ds)) {
      connectionHolder = TransactionSynchronizationManager.unbindResource(ds).asInstanceOf[ConnectionHolder]
    }
    new SuspendedResourcesHolder(sessionHolder, connectionHolder)
  }

  protected override def doResume(transaction: Object, suspendedResources: Object): Unit = {
    val resourcesHolder = suspendedResources.asInstanceOf[SuspendedResourcesHolder]
    TransactionSynchronizationManager.bindResource(sessionFactory, resourcesHolder.sessionHolder)
    if (dataSource != null && resourcesHolder.connectionHolder != null) {
      TransactionSynchronizationManager.bindResource(dataSource, resourcesHolder.connectionHolder)
    }
  }

  protected override def shouldCommitOnGlobalRollbackOnly(): Boolean = true

  protected override def doCommit(status: DefaultTransactionStatus): Unit = {
    val txObject = status.getTransaction.asInstanceOf[JpaTransactionObject]
    try {
      txObject.session.getTransaction.commit()
    } catch {
      case ex: RollbackException =>
        ex.getCause match {
          case e: RuntimeException => throw e
          case _ => throw new RuntimeException(ex)
        }
      case ex: RuntimeException => throw ex
    }
  }

  protected override def doRollback(status: DefaultTransactionStatus): Unit = {
    val txObject = status.getTransaction.asInstanceOf[JpaTransactionObject]
    try {
      val tx = txObject.session.getTransaction
      if tx.isActive then tx.rollback()
    } catch {
      case pe: PersistenceException => throw new TransactionSystemException("Could not roll back JPA transaction", pe)
    }
    finally {
      if !txObject.isNewSession then txObject.session.clear()
    }
  }

  protected override def doSetRollbackOnly(status: DefaultTransactionStatus): Unit = {
    status.getTransaction.asInstanceOf[JpaTransactionObject].setRollbackOnly()
  }

  protected override def doCleanupAfterCompletion(transaction: Object): Unit = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]
    if (txObject.isNewSession) {
      TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory)
    }
    txObject.sessionHolder.clear()

    // Remove the JDBC connection holder from the thread, if exposed.
    if (dataSource != null && txObject.hasConnectionHolder()) {
      TransactionSynchronizationManager.unbindResource(dataSource)
      val conHandle = txObject.getConnectionHolder.getConnectionHandle
      if (conHandle != null) {
        try {
          SessionHelper.releaseJdbcConnection(conHandle, txObject.session)
        }
        catch {
          case ex: Throwable => logger.error("Failed to release JDBC connection after transaction", ex)
        }
      }
    }

    SessionHelper.cleanupTransaction(txObject.transactionData)
    // Remove the entity manager holder from the thread.
    if (txObject.isNewSession) {
      SessionHelper.safeCloseSession(txObject.sessionHolder.session)
    } else {
      logger.debug("Not closing pre-bound JPA EntityManager after transaction")
    }
  }

  /**
   * JPA transaction object, representing a EntityManagerHolder.
   * Used as transaction object by JpaTransactionManager.
   */
  class JpaTransactionObject extends JdbcTransactionObjectSupport {

    var sessionHolder: SessionHolder = _
    var isNewSession: Boolean = false

    def update(sessionHolder: SessionHolder, isNewSession: Boolean): Unit = {
      this.sessionHolder = sessionHolder
      this.isNewSession = isNewSession
    }

    var data: AnyRef = _

    def hasSessionHolder: Boolean = this.sessionHolder != null

    def hasTransaction: Boolean = this.sessionHolder != null && this.sessionHolder.transactionActive

    def session: EntityManager = sessionHolder.session

    def transactionData_=(d: AnyRef): Unit = {
      this.data = d
      sessionHolder.transactionActive = true
      d match {
        case sm: SavepointManager => sessionHolder.savepointManager = sm
        case _ =>
      }
    }

    def transactionData: AnyRef = data

    def setRollbackOnly(): Unit = {
      val tx = session.getTransaction
      if tx.isActive then tx.setRollbackOnly()
      if hasConnectionHolder then getConnectionHolder.setRollbackOnly()
    }

    override def isRollbackOnly: Boolean = session.getTransaction.getRollbackOnly

    override def flush(): Unit = session.flush()

    @throws[TransactionException]
    override def createSavepoint(): Object = {
      if (sessionHolder.isRollbackOnly) {
        throw new CannotCreateTransactionException(
          "Cannot create savepoint for transaction which is already marked as rollback-only")
      }
      savepointManager.createSavepoint()
    }

    @throws[TransactionException]
    override def rollbackToSavepoint(savepoint: Object): Unit = {
      savepointManager.rollbackToSavepoint(savepoint)
      sessionHolder.resetRollbackOnly()
    }

    @throws[TransactionException]
    override def releaseSavepoint(savepoint: Object): Unit = {
      savepointManager.releaseSavepoint(savepoint)
    }

    private def savepointManager: SavepointManager = {
      if (!isSavepointAllowed()) {
        throw new NestedTransactionNotSupportedException("Transaction manager does not allow nested transactions")
      }
      val sm = sessionHolder.savepointManager
      if (sm == null) {
        throw new NestedTransactionNotSupportedException(
          "JpaDialect does not support savepoints - check your JPA provider's capabilities")
      }
      sm
    }
  }
}
