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

import org.beangle.commons.logging.Logging
import org.hibernate.{ FlushMode, HibernateException, MultiTenancyStrategy, Session, SessionFactory }
import org.hibernate.engine.jdbc.connections.spi.{ ConnectionProvider, MultiTenantConnectionProvider }
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.springframework.transaction.support.TransactionSynchronizationManager.{ bindResource, getResource, unbindResource }
import javax.sql.DataSource

/**
 * Open or Close Hibernate Session
 *
 * @author chaostone
 */
object SessionUtils extends Logging {

  private val threadBinding = new ThreadLocal[collection.mutable.HashMap[SessionFactory, Boolean]]

  def getDataSource(factory: SessionFactory): DataSource = {
    val factoryImpl = factory.asInstanceOf[SessionFactoryImplementor]
    if (MultiTenancyStrategy.NONE == factoryImpl.getSettings().getMultiTenancyStrategy()) {
      factoryImpl.getServiceRegistry.getService(classOf[ConnectionProvider]).unwrap(classOf[DataSource])
    } else {
      factoryImpl.getServiceRegistry.getService(classOf[MultiTenantConnectionProvider]).unwrap(classOf[DataSource])
    }
  }

  def enableBinding(factory: SessionFactory) {
    var maps = threadBinding.get()
    if (null == maps) {
      maps = new collection.mutable.HashMap
      threadBinding.set(maps)
    }
    maps.put(factory, true)
  }

  def isEnableBinding(factory: SessionFactory): Boolean = {
    val maps = threadBinding.get()
    if (null == maps) false else None != maps.get(factory)
  }

  def disableBinding(factory: SessionFactory) {
    val maps = threadBinding.get()
    if (null != maps) maps.remove(factory)
  }

  def openSession(factory: SessionFactory): SessionHolder = {
    var holder = getResource(factory).asInstanceOf[SessionHolder]
    var session: Session = null
    if (null == holder) {
      session = factory.openSession()
      session.setFlushMode(FlushMode.MANUAL)
      holder = new SessionHolder(session)
      if (isEnableBinding(factory)) bindResource(factory, holder)
    }
    holder
  }

  def currentSession(factory: SessionFactory): SessionHolder = getResource(factory).asInstanceOf[SessionHolder]

  def closeSession(factory: SessionFactory) {
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

  def closeSession(session: Session) {
    try {
      val holder = getResource(session.getSessionFactory()).asInstanceOf[SessionHolder]
      if (null != holder) unbindResource(session.getSessionFactory())
      session.close()
    } catch {
      case ex: HibernateException => logger.debug("Could not close Hibernate Session", ex)
      case e: Throwable => logger.debug("Unexpected exception on closing Hibernate Session", e)
    }
  }

  def toString(session: Session): String = {
    return session.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(session))
  }
}
