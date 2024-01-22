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

import org.beangle.data.model.pojo.Named
import org.beangle.data.orm.*

class TestMapping3 extends MappingModule {

  override def binding(): Unit = {
    autoIncrement()

    bind[TestUser].declare { e =>
      e.properties.is(depends("user"), readOnly)
      e.friends is eleColumn("friend_user_id")
      e.tags.is(table("users_tags"), keyLength(30), eleColumn("value2"), eleLength(200))
      e.updatedAt is default("current")
    }

    bind[TestRole].declare { e =>
      e.name is unique
      e.vocations.is(joinColumn("role_id"), eleColumn("exclude_on"))
      e.properties is keyColumn("type_id")
      e.users is many2many("roles")
    }

    bind[UserProperty]

    bind[UrlMenu].declare { c =>
      c.url.is(notnull, length(40))
      c.parent is target[UrlMenu]
      index("idx_menu_name", true, c.name)
    }

    bind[Named].declare { c =>
      c.name is length(13)
    }

    bind[AccessLog].declare { e =>
      e.ip is partitionKey
      index("", true, e.username, e.ip)
    }.generator(IdGenerator.Native)

    all.except(classOf[AccessLog]).cacheAll()
  }
}
