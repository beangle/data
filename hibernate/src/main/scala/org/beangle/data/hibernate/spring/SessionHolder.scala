/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate.spring

import org.beangle.commons.lang.Assert
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.Transaction
import org.springframework.transaction.support.ResourceHolderSupport

/**
 * @author chaostone
 */
class SessionHolder(val session: Session) extends ResourceHolderSupport {

  Assert.notNull(session, "Session must not be null")

  var transaction: Transaction = _

  var previousFlushMode: FlushMode = _

  override def clear(): Unit = {
    super.clear()
    this.transaction = null
    this.previousFlushMode = null
  }

}
