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

package org.beangle.data.orm.model

import org.beangle.commons.collection.Collections
import org.beangle.data.model.Entity

import java.time.{LocalDate, YearMonth}

class Role(var id: Int) extends Entity[Int] with Coded {
  this.code = String.valueOf(System.identityHashCode(this))
  var name: String = "Role" + String.valueOf(System.identityHashCode(this))

  def this() = this(0)

  var parent: Option[Role] = None
  var children = Collections.newBuffer[Role]
  var createdAt: java.util.Date = new java.util.Date(System.currentTimeMillis)
  var expiredOn: java.sql.Date = new java.sql.Date(System.currentTimeMillis)
  var updatedAt: java.sql.Timestamp = new java.sql.Timestamp(System.currentTimeMillis)
  var s: java.util.Calendar = java.util.Calendar.getInstance

  var creator: Option[User] = None
  var startOn: Option[YearMonth]  = None
  var properties = Collections.newMap[Int, Boolean]
}

class ExtendRole(id: Int) extends Role(id) {
  var enName: String = "role" + id
  def this() = this(0)
}
