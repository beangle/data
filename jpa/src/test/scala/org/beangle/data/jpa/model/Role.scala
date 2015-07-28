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
package org.beangle.data.jpa.model

import org.beangle.data.model.Entity
import org.beangle.commons.collection.Collections

class Role(var id: java.lang.Integer) extends Entity[java.lang.Integer] with Coded {
  def this() = this(0)
  var name: String = _

  var parent: Role = _
  var childred = Collections.newBuffer[Role]
}

class ExtendRole(id: Integer) extends Role(id) {
  var enName: String = _
  def this() = this(0)
}