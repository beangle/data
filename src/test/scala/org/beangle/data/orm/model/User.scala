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

import org.beangle.commons.json.{Json, JsonArray, JsonObject}
import org.beangle.commons.lang.time.{WeekDay, WeekState, WeekTime}
import org.beangle.data.model.pojo.{Hierarchical, Named, Remark}
import org.beangle.data.model.{Component, Entity, LongId, StringId}

import scala.collection.mutable

class User(var id: Long) extends Entity[Long] {
  def this() = this(0)

  var name: Name = _
  var roleSet: java.util.Set[Role] = new java.util.HashSet[Role]
  var age: Option[Int] = None
  var money: Short = 30
  var properties: mutable.Map[String, String] = _
  var occupy: WeekState = new WeekState(0)
  var weekday: WeekDay = WeekDay.Sun

  var createdOn = new java.sql.Date(System.currentTimeMillis)
  var role: Option[Role] = _
  var roleList: mutable.Buffer[Role] = new mutable.ListBuffer[Role]
  var member: Member = _
  var skills: mutable.Map[SkillType, Skill] = _

  var profiles: mutable.Set[Profile] = new mutable.HashSet[Profile]
  //  var profiles: java.util.Set[Profile] = new java.util.HashSet[Profile]
  var times: mutable.Map[Int, WeekTime] = new mutable.HashMap[Int, WeekTime]

  var friends: JsonArray = new JsonArray()
  var charactor: JsonObject = new JsonObject()
  var remark: Option[Json] = None
}

class SkillType extends LongId {
  var name: String = _
}

class Skill extends LongId {
  var skillType: SkillType = _
  var name: String = _
}

class Name extends Component {
  var first: String = _
  var last: String = _
}

class Member extends Component {
  @scala.beans.BeanProperty
  var user: User = _
  var granter: Option[Role] = None
  var admin: Boolean = _
  var roles: collection.mutable.Set[Role] = _
}

trait Coded {
  var code: String = _
}

abstract class CodedEntity extends StringId with Coded

abstract class StringIdCodedEntity extends CodedEntity

class MenuItem extends StringIdCodedEntity

class Department extends LongId with Hierarchical[Department] with Named with Remark
