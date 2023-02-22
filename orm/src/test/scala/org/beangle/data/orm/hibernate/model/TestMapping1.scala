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

package org.beangle.data.orm.hibernate.model

import org.beangle.data.orm.{IdGenerator, MappingModule}

object TestMapping1 extends MappingModule {

  def binding(): Unit = {
    defaultCache("test_cache_region", "read-write")

    bind[LongIdResource]
    bind[LongDateIdResource]
    bind[IntIdResource]

    bind[Coded].declare { c =>
      c.code is(notnull, length(20), unique)
    }

    bind[Profile]
    bind[User].declare { e =>
      e.name.first is(unique, column("first_name"))
      e.name.first & e.name.last & e.createdOn are notnull
      e.roleList is(ordered, table("users_roles_list"))
      e.profiles is depends("user")
      e.properties is(table("users_props"), eleColumn("value2"), eleLength(200))
    }.generator(IdGenerator.Assigned)

    bind[SkillType]
    bind[Skill].table("skill_list")

    bind[Course].declare { e =>
      e.levels is depends("course")
    }.generator(IdGenerator.Native)

    bind[CourseLevel].declare { e =>
      index("", true, e.course, e.level)
    }.generator(IdGenerator.Native)
  }
}
