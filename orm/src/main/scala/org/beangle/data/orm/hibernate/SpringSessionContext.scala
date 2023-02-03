package org.beangle.data.orm.hibernate

import org.hibernate.Session
import org.hibernate.context.spi.CurrentSessionContext
import org.hibernate.engine.spi.SessionFactoryImplementor

/**
 * @author chaostone
 */
class SpringSessionContext(val sessionFactory: SessionFactoryImplementor) extends CurrentSessionContext {

  /**
   * Retrieve the Spring-managed Session for the current thread, if any.
   */
  def currentSession: Session = {
    val sh = SessionHelper.currentSession(this.sessionFactory)
    if sh == null then SessionHelper.openSession(this.sessionFactory).session
    else sh.session
  }
}

