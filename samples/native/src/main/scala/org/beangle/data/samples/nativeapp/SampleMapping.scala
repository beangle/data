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

import org.beangle.data.orm.{IdGenerator, MappingModule}

object SampleMapping extends MappingModule {
  def binding(): Unit = {
    bind[Department].declare { d =>
      d.code is(notnull, length(20), unique)
      d.name is(notnull, length(100))
      d.parent is target(classOf[Department])
    }.cacheable().generator(IdGenerator.Native)

    bind[Role].declare { r =>
      r.code is(notnull, unique, length(20))
      r.name is(notnull, length(100))
    }.generator(IdGenerator.Native)

    bind[Employee].declare { e =>
      e.code is(notnull, unique, length(20))
      e.name is(notnull, length(100))
      e.department is target(classOf[Department])
      e.boss is target(classOf[Employee])
      e.roles is depends("employee")
      e.tags.is(table("emp_tags"), keyLength(30), eleColumn("tag_value"), eleLength(200))
    }.cacheable().generator(IdGenerator.Native)

    bind[Course].declare { c =>
      c.code is(notnull, unique, length(30))
      c.name is(notnull, length(100))
    }.generator(IdGenerator.Code)
  }
}
