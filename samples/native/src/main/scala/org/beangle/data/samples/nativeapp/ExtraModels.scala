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

package org.beangle.data.samples.nativeapp

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.math.Decimal5
import org.beangle.data.model.{IntId, LongId}
import org.beangle.data.model.pojo.{Coded, Named, Updatable}

import scala.collection.mutable

/** Extended entity set: associations, collections, enum, value types and code id. */
enum EmpLevel(val id: Int) {
  case Junior extends EmpLevel(1)
  case Senior extends EmpLevel(2)
  case Lead extends EmpLevel(3)
}

class Role extends LongId, Coded, Named {
  var employee: Employee = _
}

class Employee extends LongId, Coded, Named, Updatable {
  var department: Department = _
  var boss: Option[Employee] = None
  var level: EmpLevel = EmpLevel.Junior
  var salary: Option[Decimal5] = None
  var roles: mutable.Set[Role] = Collections.newSet[Role]
  var tags: mutable.Map[String, String] = Collections.newMap[String, String]
}

class Course extends IntId, Coded, Named

