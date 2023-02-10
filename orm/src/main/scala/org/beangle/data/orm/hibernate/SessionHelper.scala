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

import jakarta.persistence.{EntityManager, PersistenceException}
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.orm.hibernate.HibernateTransactionManager.SessionHolder
import org.beangle.data.orm.hibernate.SessionHelper.HibernateConnectionHandle
import org.hibernate.*
import org.hibernate.engine.jdbc.connections.spi.{ConnectionProvider, MultiTenantConnectionProvider}
import org.hibernate.engine.spi.{SessionFactoryImplementor, SessionImplementor}
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode
import org.springframework.jdbc.datasource.ConnectionHandle
import org.springframework.transaction.support.ResourceTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager.{bindResource, getResource, unbindResource}
import org.springframework.transaction.{InvalidIsolationLevelException, TransactionDefinition, TransactionException}

import java.sql.{Connection, SQLException}
import java.util.function.Consumer
import javax.sql.DataSource

object SessionHelper extends Logging {
  class HibernateConnectionHandle(session: SessionImplementor) extends ConnectionHandle {
    override def getConnection: Connection = {
      session.getJdbcCoordinator.getLogicalConnection.getPhysicalConnection
    }
  }

  class SessionTransactionData(session: SessionImplementor, previousFlushMode: FlushMode, needsConnectionReset: Boolean, previousIsolationLevel: Integer, readOnly: Boolean) {

    def resetSessionState(): Unit = {
      if (this.previousFlushMode != null) {
        this.session.setHibernateFlushMode(this.previousFlushMode)
      }
      if (this.needsConnectionReset &&
        this.session.getJdbcCoordinator.getLogicalConnection.isPhysicallyConnected) {
        val con = this.session.getJdbcCoordinator.getLogicalConnection.getPhysicalConnection
        DataSourceUtils.resetConnectionAfterTransaction(con, this.previousIsolationLevel, this.readOnly)
      }
    }
  }

  @throws[PersistenceException]
  @throws[SQLException]
  @throws[TransactionException]
  def beginTransaction(entityManager: EntityManager, definition: TransactionDefinition): AnyRef = {
    val session = getSession(entityManager)

    if (definition.getTimeout != TransactionDefinition.TIMEOUT_DEFAULT) {
      session.getTransaction.setTimeout(definition.getTimeout)
    }

    val isolationLevelNeeded = definition.getIsolationLevel != TransactionDefinition.ISOLATION_DEFAULT
    var previousIsolationLevel: Integer = null
    var preparedCon: Connection = null

    if (isolationLevelNeeded || definition.isReadOnly) {
      if (ConnectionReleaseMode.ON_CLOSE.equals(session.getJdbcCoordinator.getLogicalConnection.getConnectionHandlingMode.getReleaseMode)) {
        preparedCon = session.getJdbcCoordinator.getLogicalConnection.getPhysicalConnection
        previousIsolationLevel = prepareConnectionForTransaction(preparedCon, definition)
      } else if (isolationLevelNeeded) {
        throw new InvalidIsolationLevelException("HibernateJpaDialect is not allowed to support custom isolation levels.");
      }
    }

    // Standard JPA transaction begin call for full JPA context setup...
    entityManager.getTransaction.begin()

    // Adapt flush mode and store previous isolation level, if any.
    var previousFlushMode = prepareFlushMode(session, definition.isReadOnly)
    definition match {
      case rtd: ResourceTransactionDefinition =>
        if (rtd.isLocalResource) {
          previousFlushMode = null
          if definition.isReadOnly then session.setDefaultReadOnly(true)
        }
      case _ =>
    }
    new SessionTransactionData(session, previousFlushMode, preparedCon != null, previousIsolationLevel, definition.isReadOnly)
  }

  private def prepareFlushMode(session: Session, readOnly: Boolean): FlushMode = {
    val flushMode = session.getHibernateFlushMode
    // suppress flushing for a read-only transaction.
    if (readOnly) {
      if (flushMode != FlushMode.MANUAL) {
        session.setHibernateFlushMode(FlushMode.MANUAL)
        return flushMode
      }
    } else { //  AUTO or COMMIT for a non-read-only transaction.
      if (flushMode.lessThan(FlushMode.COMMIT)) {
        session.setHibernateFlushMode(FlushMode.AUTO)
        return flushMode;
      }
    }
    null
  }

  def cleanupTransaction(transactionData: Object): Unit = {
    transactionData match {
      case std: SessionTransactionData => std.resetSessionState()
      case _ =>
    }
  }

  def releaseJdbcConnection(conHandle: ConnectionHandle, em: EntityManager): Unit = {}

  def getJdbcConnection(entityManager: EntityManager, readOnly: Boolean): ConnectionHandle = {
    new HibernateConnectionHandle(getSession(entityManager))
  }

  protected def getSession(entityManager: EntityManager): SessionImplementor = {
    entityManager.unwrap(classOf[SessionImplementor])
  }

  def safeCloseSession(em: EntityManager): Unit = {
    if (em != null) try if (em.isOpen) em.close()
    catch {
      case ex: Throwable => logger.error("Failed to release JPA EntityManager", ex)
    }
  }

  def getDataSource(factory: SessionFactory): DataSource = {
    val factoryImpl = factory.asInstanceOf[SessionFactoryImplementor]
    if (factoryImpl.getSessionFactoryOptions.isMultiTenancyEnabled) {
      factoryImpl.getServiceRegistry.getService(classOf[MultiTenantConnectionProvider]).unwrap(classOf[DataSource])
    } else {
      factoryImpl.getServiceRegistry.getService(classOf[ConnectionProvider]).unwrap(classOf[DataSource])
    }
  }

  @throws[SQLException]
  private def prepareConnectionForTransaction(con: Connection, definition: TransactionDefinition): Integer = {
    // Set read-only flag.
    if (definition != null && definition.isReadOnly) try {
      con.setReadOnly(true)
    } catch {
      case ex@(_: SQLException | _: RuntimeException) =>
        var exToCheck: Throwable = ex
        while (exToCheck != null) {
          if exToCheck.getClass.getSimpleName.contains("Timeout") then throw ex
          exToCheck = exToCheck.getCause
        }
    }
    var previousIsolationLevel: Integer = null
    if (definition != null && (definition.getIsolationLevel ne TransactionDefinition.ISOLATION_DEFAULT)) {
      val currentIsolation = con.getTransactionIsolation
      if (currentIsolation != definition.getIsolationLevel) {
        previousIsolationLevel = currentIsolation
        con.setTransactionIsolation(definition.getIsolationLevel)
      }
    }
    previousIsolationLevel
  }

  private def doOpenSession(factory: SessionFactory,
                            interceptor: Option[Interceptor],
                            initializer: Option[Consumer[Session]]) = {
    val s = interceptor match {
      case Some(i) =>
        val builder = factory.withOptions()
        builder.interceptor(i)
        builder.openSession()
      case None => factory.openSession()
    }
    initializer foreach { iz => iz.accept(s) }
    s
  }

  def openSession(factory: SessionFactory,
                  interceptor: Option[Interceptor] = None,
                  initializer: Option[Consumer[Session]] = None): SessionHolder = {
    var holder = getResource(factory).asInstanceOf[SessionHolder]
    var session: Session = null
    if (null == holder) {
      session = doOpenSession(factory, interceptor, initializer)
      session.setHibernateFlushMode(FlushMode.COMMIT)
      holder = new SessionHolder(session)
      bindResource(factory, holder)
    }
    holder
  }

  def currentSession(factory: SessionFactory): SessionHolder = {
    getResource(factory).asInstanceOf[SessionHolder]
  }

  def closeSession(factory: SessionFactory): Unit = {
    try {
      val holder = getResource(factory).asInstanceOf[SessionHolder]
      if (null != holder) {
        unbindResource(factory)
        holder.session.close()
      }
    } catch {
      case ex: HibernateException => logger.debug("Could not close Hibernate Session", ex)
      case e: Throwable => logger.debug("Unexpected exception on closing Hibernate Session", e)
    }
  }

  def closeSession(session: Session): Unit = {
    try {
      val holder = getResource(session.getSessionFactory).asInstanceOf[SessionHolder]
      if (null != holder) unbindResource(session.getSessionFactory)
      session.close()
    } catch {
      case ex: HibernateException => logger.debug("Could not close Hibernate Session", ex)
      case e: Throwable => logger.debug("Unexpected exception on closing Hibernate Session", e)
    }
  }

  def toString(session: Session): String = {
    session.getClass.getName + "@" + Integer.toHexString(System.identityHashCode(session))
  }
}
