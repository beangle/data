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

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.typeTag
import org.beangle.commons.lang.time.WeekState
import org.beangle.data.model.Entity
import org.beangle.data.model.Component
import org.beangle.data.model.StringId

class User(var id: java.lang.Long) extends Entity[java.lang.Long] {
  def this() = this(0)
  var name: Name = _
  var roleList: collection.mutable.Seq[Role] = new collection.mutable.ListBuffer[Role]
  var roleSet: collection.mutable.Set[Role] = new collection.mutable.HashSet[Role]
  var age: Option[Int] = None
  var properties: collection.mutable.Map[String, String] = _
  var occupy: WeekState = _
  var createdOn: java.sql.Date = _
}

class Name extends Component {
  var first: String = _
  var last: String = _
}

class Member extends Component {
  var granter: Role = _
  var admin: Boolean = _
  var roles: collection.mutable.Set[Role] = new collection.mutable.HashSet[Role]
}

trait Coded{
  var code:String=_
}

abstract class CodedEntity   extends StringId with Coded

abstract class StringIdCodedEntity   extends CodedEntity

class UserAb(var id: java.lang.Long) extends Entity[java.lang.Long] with Coded {
  def this() = this(0)
  var name: Name = _
  var age: Option[Integer] = None
  var role: Role = _
  var roleList: collection.mutable.Seq[Role] = new collection.mutable.ListBuffer[Role]
  var properties: collection.mutable.Map[String, String] = _
  var member: Member = _
}

class Menu extends StringIdCodedEntity



