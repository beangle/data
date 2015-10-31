/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.hibernate.model

import org.beangle.data.model.Entity
import org.beangle.commons.collection.Collections

class Role(var id: Int) extends Entity[Int] with Coded {
  this.code = String.valueOf(System.identityHashCode(this))
  var name: String = "Role" + String.valueOf(System.identityHashCode(this))

  def this() = this(0)

  var parent: Role = _
  var children = Collections.newBuffer[Role]
  var createdAt: java.util.Date = _
  var expiredOn: java.sql.Date = _
  var updatedAt: java.sql.Timestamp = _
  var s: java.util.Calendar = _
}

class ExtendRole(id: Int) extends Role(id) {
  var enName: String = _
  def this() = this(0)
}