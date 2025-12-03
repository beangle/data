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

import jakarta.persistence.EntityManager
import org.beangle.commons.logging.Logging
import org.beangle.data.orm.hibernate.HibernateTransactionManager.SessionHolder
import org.hibernate.*
import org.hibernate.engine.jdbc.connections.spi.{ConnectionProvider, MultiTenantConnectionProvider}
import org.hibernate.engine.spi.{SessionFactoryImplementor, SessionImplementor}
import org.springframework.jdbc.datasource.{ConnectionHandle, ConnectionHolder}
import org.springframework.transaction.support.TransactionSynchronizationManager as Tsm

import java.sql.Connection
import java.util.function.Consumer
import javax.sql.DataSource

object SessionHelper extends Logging {
  private class HibernateConnectionHandle(session: SessionImplementor) extends ConnectionHandle {
    override def getConnection: Connection = {
      session.getJdbcCoordinator.getLogicalConnection.getPhysicalConnection
    }
  }

  def getConnectionHolder(session: SessionImplementor): ConnectionHolder = {
    new ConnectionHolder(new HibernateConnectionHandle(session))
  }

  def getDataSource(factory: SessionFactory): DataSource = {
    val factoryImpl = factory.asInstanceOf[SessionFactoryImplementor]
    if (factoryImpl.getSessionFactoryOptions.isMultiTenancyEnabled) {
      factoryImpl.getServiceRegistry.getService(classOf[MultiTenantConnectionProvider[_]]).unwrap(classOf[DataSource])
    } else {
      factoryImpl.getServiceRegistry.getService(classOf[ConnectionProvider]).unwrap(classOf[DataSource])
    }
  }

  def doOpenSession(factory: SessionFactory,
                    interceptor: Option[Interceptor] = None,
                    initializer: Option[Consumer[Session]] = None): SessionImplementor = {
    val s = interceptor match {
      case Some(i) =>
        val builder = factory.withOptions()
        builder.interceptor(i)
        builder.openSession()
      case None => factory.openSession()
    }
    initializer foreach { iz => iz.accept(s) }
    s.asInstanceOf[SessionImplementor]
  }

  def openSession(factory: SessionFactory,
                  interceptor: Option[Interceptor] = None,
                  initializer: Option[Consumer[Session]] = None): SessionHolder = {
    var holder = Tsm.getResource(factory).asInstanceOf[SessionHolder]
    var session: SessionImplementor = null
    if (null == holder) {
      session = doOpenSession(factory, interceptor, initializer)
      session.setHibernateFlushMode(FlushMode.COMMIT)
      holder = new SessionHolder(session)
      Tsm.bindResource(factory, holder)
    }
    holder
  }

  def currentSession(factory: SessionFactory): SessionHolder = {
    Tsm.getResource(factory).asInstanceOf[SessionHolder]
  }

  def closeSession(factory: SessionFactory): Unit = {
    val holder = Tsm.unbindResourceIfPossible(factory).asInstanceOf[SessionHolder]
    if null != holder then safeCloseSession(holder.session)
  }

  def closeSession(session: Session): Unit = {
    Tsm.unbindResourceIfPossible(session.getSessionFactory)
    safeCloseSession(session)
  }

  def safeCloseSession(em: EntityManager): Unit = {
    if (em != null) {
      try {
        if (em.isOpen) em.close()
      } catch {
        case ex: Throwable => logger.error("Failed to release session", ex)
      }
    }
  }

  def safeCloseSession(s: StatelessSession): Unit = {
    if (s != null && s.isOpen) s.close()
  }

  def toString(session: Session): String = {
    session.getClass.getName + "@" + Integer.toHexString(System.identityHashCode(session))
  }
}
