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

package org.beangle.data.orm

import org.beangle.commons.collection.Collections
import org.beangle.data.model.LongId

import java.time.LocalDate
import scala.collection.mutable
import scala.compiletime.uninitialized

class AccessLog extends LongId {

  var resource: String = uninitialized
  var username: String = uninitialized
  var action: String = uninitialized
  var ip: String = uninitialized
  var updatedOn: LocalDate = uninitialized
  var params: mutable.Buffer[AccessParam] = Collections.newBuffer[AccessParam]
  var userAgent: String = uninitialized

  def addParam(id: Long, name: String, value: String): Unit = {
    val param = new AccessParam
    param.id = id
    param.log = this
    param.name = name
    param.value = value
    param.updatedOn = this.updatedOn
    params += param
  }
}

class AccessParam extends LongId {
  var log: AccessLog = uninitialized
  var updatedOn: LocalDate = uninitialized
  var name: String = uninitialized
  var value: String = uninitialized
}
