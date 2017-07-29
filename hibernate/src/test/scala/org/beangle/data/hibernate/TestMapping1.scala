/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
package org.beangle.data.hibernate

import scala.reflect.runtime.universe
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.time.WeekState
import org.beangle.data.hibernate.model.{ Coded, IntIdResource, LongDateIdResource, LongIdResource, Skill, SkillType, User }
import org.beangle.commons.lang.time.WeekDay
import org.beangle.data.hibernate.model.Profile
import org.beangle.data.orm.MappingModule

object TestMapping1 extends MappingModule {

  def binding(): Unit = {
    defaultIdGenerator("seq_per_table")
    defaultCache("test_cache_region", "read-write")

    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].on(c => declare(
      c.code is (notnull, length(20))))

    bind[Profile]
    bind[User].on(e => declare(
      e.name.first is (unique, column("first_name")),
      e.name.first & e.name.last & e.createdOn are notnull,
      e.roleList is (ordered, table("users_roles_list")),
      e.profiles is depends("user"),
      e.properties is (table("users_props"), eleColumn("value2"), eleLength(200)))).generator("native")

    bind[SkillType]
    bind[Skill].table("skill_list")
  }
}
