/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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

import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.time.WeekState
import org.beangle.commons.model.{ Component, Entity, Hierarchical, LongId, StringId }
import org.beangle.commons.lang.time.WeekDay._
import org.beangle.commons.model.Remark

class User(var id: Long) extends Entity[Long] {
  def this() = this(0)
  var name: Name = _
  var roleSet: java.util.Set[Role] = new java.util.HashSet[Role]
  var age: Option[Int] = None
  var money: Short = _
  var properties: collection.mutable.Map[String, String] = _
  var occupy: WeekState = new WeekState(0)
  var weekday: WeekDay = Sun

  var createdOn = new java.sql.Date(System.currentTimeMillis)
  var role: Option[Role] = _
  var roleList: collection.mutable.Buffer[Role] = new collection.mutable.ListBuffer[Role]
  var member: Member = _
  var skills: collection.mutable.Map[SkillType, Skill] = _

  var profiles: collection.mutable.Set[Profile] = new collection.mutable.HashSet[Profile]
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

class Menu extends StringIdCodedEntity

class Department extends LongId with Hierarchical[Department] with Remark
