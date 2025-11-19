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

import jakarta.persistence.{EntityManager, EntityManagerFactory}
import org.beangle.data.orm.hibernate.HibernateTransactionManager.*
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.{TransactionException as HibernateTransactionException, *}
import org.springframework.jdbc.datasource.{ConnectionHolder, DataSourceUtils, JdbcTransactionObjectSupport}
import org.springframework.transaction.*
import org.springframework.transaction.support.{TransactionSynchronizationManager as Tsm, *}

import javax.sql.DataSource

object HibernateTransactionManager {

  /** 移植自spring orm的SessionHolder
   *  去掉transactionActive,savepointManager
   *  增加了statelessSession,transaction,previousFlushMode
   * @param session session
   */
  class SessionHolder(val session: Session) extends ResourceHolderSupport {
    var statelessSession: StatelessSession = _
    var transaction: Transaction = _
    var previousFlushMode: FlushMode = _

    def closeAll(): Unit = {
      SessionHelper.safeCloseSession(session)
      if (this.statelessSession != null && this.statelessSession.isOpen) this.statelessSession.close()
    }
  }

  class SuspendedResourcesHolder(val sessionHolder: SessionHolder, val connectionHolder: ConnectionHolder)

  /**
   * JPA transaction object, representing a EntityManagerHolder.
   * Used as transaction object by JpaTransactionManager.
   */
  class JpaTransactionObject(private var holder: SessionHolder = null,
                             private var newHolder: Boolean = false,
                             private var newSession: Boolean = false)
    extends JdbcTransactionObjectSupport {

    var needsConnectionReset = false

    def update(sessionHolder: SessionHolder, newHolder: Boolean, newSession: Boolean): Unit = {
      this.holder = sessionHolder
      this.newHolder = newHolder
      this.newSession = newSession
    }

    def setSession(session: Session): Unit = {
      this.holder = new SessionHolder(session)
      this.newHolder = true
      this.newSession = true
    }

    def isNewSession: Boolean = this.newSession

    def isNewSessionHolder: Boolean = this.newHolder

    def sessionHolder: SessionHolder = this.holder

    def setSessionHolder(sessionHolder: SessionHolder): Unit = {
      this.holder = sessionHolder
      this.newHolder = false
      this.newSession = false
    }

    def connectionPrepared(): Unit = {
      this.needsConnectionReset = true
    }

    def hasSessionHolder: Boolean = this.holder != null

    def hasTransaction: Boolean = this.holder != null && this.holder.transaction != null

    def session: EntityManager = holder.session

    def setRollbackOnly(): Unit = {
      holder.setRollbackOnly()
      if (hasConnectionHolder) getConnectionHolder.setRollbackOnly()
    }

    override def isRollbackOnly: Boolean = {
      holder.isRollbackOnly || (hasConnectionHolder && getConnectionHolder.isRollbackOnly)
    }

    override def flush(): Unit = session.flush()
  }
}

/** 处理Hibernate中的事务
 *
 * @see spring-orm中的HibernateTransactionManager
 *      <ol>
 *      <li>禁用了hibernate实体拦截器(EntityInterceptor)</li>
 *      <li>Let hibernateManagedSession = false</li>
 *      <li>Disable exceptionTranslator</li>
 *      <li>Force prepareConnection = true</li>
 *      </ol>
 * @param sessionFactory EntityManagerFactory
 */
class HibernateTransactionManager(val sessionFactory: EntityManagerFactory)
  extends AbstractPlatformTransactionManager, ResourceTransactionManager {

  var dataSource: DataSource = _

  var jpaPropertyMap: java.util.Map[String, Object] = _

  override def getResourceFactory: Object = sessionFactory

  protected override def doGetTransaction(): Object = {
    val txObject = new JpaTransactionObject()
    txObject.setSavepointAllowed(isNestedTransactionAllowed)

    var emHolder = Tsm.getResource(sessionFactory).asInstanceOf[SessionHolder]
    if (null == emHolder) {
      // Beangle Added. for avoid openSession in view explicitly.
      emHolder = SessionHelper.openSession(sessionFactory.asInstanceOf[SessionFactory])
      txObject.update(emHolder, false, false)
    } else {
      txObject.setSessionHolder(emHolder)
    }

    val con = Tsm.getResource(dataSource).asInstanceOf[ConnectionHolder]
    txObject.setConnectionHolder(con)
    txObject
  }

  protected override def isExistingTransaction(transaction: Object): Boolean = {
    transaction.asInstanceOf[JpaTransactionObject].hasTransaction
  }

  protected override def doBegin(transaction: Object, definition: TransactionDefinition): Unit = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]

    if (txObject.hasConnectionHolder && !txObject.getConnectionHolder.isSynchronizedWithTransaction) {
      throw new IllegalTransactionStateException("Pre-bound JDBC Connection found!")
    }

    var em: SessionImplementor = null
    try {
      if (!txObject.hasSessionHolder || txObject.sessionHolder.isSynchronizedWithTransaction) {
        val newEm = createEntityManagerForTransaction()
        txObject.setSession(newEm)
      }
      em = txObject.session.unwrap(classOf[SessionImplementor])

      val isolationLevelNeeded = definition.getIsolationLevel != TransactionDefinition.ISOLATION_DEFAULT
      if (isolationLevelNeeded || definition.isReadOnly) {
        val conn = em.getJdbcCoordinator.getLogicalConnection
        if (ConnectionReleaseMode.ON_CLOSE == conn.getConnectionHandlingMode.getReleaseMode) {
          val con = conn.getPhysicalConnection
          val previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition)
          txObject.setPreviousIsolationLevel(previousIsolationLevel)
          txObject.setReadOnly(definition.isReadOnly)
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
      val conHandle = SessionHelper.getJdbcConnection(em)
      val conHolder = new ConnectionHolder(conHandle)
      if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
        conHolder.setTimeoutInSeconds(timeout)
      }
      Tsm.bindResource(dataSource, conHolder)
      txObject.setConnectionHolder(conHolder)

      // Bind the entity manager holder to the thread.
      if (txObject.isNewSessionHolder && !Tsm.hasResource(sessionFactory)) {
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

  protected override def doSuspend(transaction: Object): Object = {
    val txObject = transaction.asInstanceOf[JpaTransactionObject]
    txObject.setSessionHolder(null)
    val sessionHolder = Tsm.unbindResource(sessionFactory).asInstanceOf[SessionHolder]
    txObject.setConnectionHolder(null)
    val conHolder = Tsm.unbindResource(dataSource).asInstanceOf[ConnectionHolder]
    new SuspendedResourcesHolder(sessionHolder, conHolder)
  }

  protected override def doResume(transaction: Object, suspendedResources: Object): Unit = {
    val resourcesHolder = suspendedResources.asInstanceOf[SuspendedResourcesHolder]
    if (Tsm.hasResource(sessionFactory)) {
      Tsm.unbindResource(sessionFactory)
    }
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
    if (txObject.isNewSessionHolder) {
      Tsm.unbindResourceIfPossible(sessionFactory)
    }
    // Remove the JDBC connection holder from the thread, if exposed.
    Tsm.unbindResource(dataSource)

    val session = txObject.sessionHolder.session.unwrap(classOf[SessionImplementor])
    if (txObject.needsConnectionReset && session.getJdbcCoordinator.getLogicalConnection.isPhysicallyConnected) {
      val con = session.getJdbcCoordinator.getLogicalConnection.getPhysicalConnection
      DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel, txObject.isReadOnly)
    }

    if (txObject.isNewSession) {
      txObject.sessionHolder.closeAll()
    } else {
      if (txObject.sessionHolder.previousFlushMode != null) {
        session.setHibernateFlushMode(txObject.sessionHolder.previousFlushMode)
      }
      session.getJdbcCoordinator.getLogicalConnection.manualDisconnect();
    }
    txObject.sessionHolder.clear()
  }
}
