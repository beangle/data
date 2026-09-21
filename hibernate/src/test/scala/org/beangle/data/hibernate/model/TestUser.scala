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

package org.beangle.data.hibernate.model

import org.beangle.commons.lang.time.{WeekDay, WeekTime}
import org.beangle.data.model.pojo.Updatable
import org.beangle.data.model.{Component, LongId}

import scala.collection.mutable
import scala.compiletime.uninitialized

/**
 * @author chaostone
 */
class TestUser extends LongId , Updatable {

  var member: NamedMember = uninitialized

  var role: TestRole = uninitialized

  var friends: collection.mutable.HashSet[TestUser] = uninitialized

  var properties: collection.mutable.HashSet[UserProperty] = uninitialized

  var tags: collection.mutable.Map[String, String] = uninitialized

  var times: collection.mutable.Map[Int, WeekTime] = uninitialized

  var roles: mutable.Buffer[TestRole] = uninitialized

  var birthday: WeekDay = WeekDay.Sun
}

class NamedMember extends Component {
  var name: Name2 = uninitialized
  var middleName: String = uninitialized
}

class Name2 extends Component {
  var firstName: String = uninitialized
  var lastName: String = uninitialized
}
