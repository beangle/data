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

import org.beangle.data.orm.MappingModule

object TestMapping2 extends MappingModule {

  def binding(): Unit = {
    defaultCache("test_cache_region", "read-write")

    bind[Role].declare { r =>
      r.name is(length(112), unique)
      //r.code is (length(20 + 1), unique) //override bingding in Coded
      r.parent is target[Role]
      r.properties is keyColumn("tag_id")
      r.children is(depends("parent"), cacheable)
    }.generator("assigned")

    bind[ExtendRole](classOf[Role].getName)

    bind[CodedEntity].declare { c =>
      c.code is(length(22), unique)
    }

    bind[StringIdCodedEntity].declare { c =>
      c.code is length(28)
    }.generator("assigned")

    bind[MenuItem]

    cache().add(collection[Role]("children"))

    bind[Department].declare { e =>
      e.children is one2many("parent")
      index("idx_department_name", true, e.name)
    }
  }
}
