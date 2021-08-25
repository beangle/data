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

import org.beangle.commons.lang.time.WeekTime
import org.beangle.commons.lang.time.WeekDay
import org.beangle.data.model.pojo.Updated
import org.beangle.data.model.{Component, LongId}

import scala.collection.mutable

/**
 * @author chaostone
 */
class TestUser extends LongId with Updated {

  var member: NamedMember = _

  var role: TestRole = _

  var friends: collection.mutable.HashSet[TestUser] = _

  var properties: collection.mutable.HashSet[UserProperty] = _

  var tags: collection.mutable.Map[String, String] = _

  var times: collection.mutable.Map[Int, WeekTime] = _

  var roles: mutable.Buffer[TestRole] = _

  var birthday:WeekDay=WeekDay.Sun
}

class NamedMember extends Component {
  var name: Name2 = _
  var middleName: String = _
}

class Name2 extends Component {
  var firstName: String = _
  var lastName: String = _
}
