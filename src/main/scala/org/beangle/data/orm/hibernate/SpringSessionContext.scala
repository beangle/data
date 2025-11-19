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

import org.hibernate.Session
import org.hibernate.context.spi.CurrentSessionContext
import org.hibernate.engine.spi.SessionFactoryImplementor

/**
 * 简化版的SpringSessionContext，仅仅存取session，没有其他设置
 *
 * @see org.springframework.orm.jpa.hibernate.SpringSessionContext
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
