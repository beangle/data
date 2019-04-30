/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.orm

import org.beangle.data.model.Entity

class TestModule extends MappingModule {

  override def binding(): Unit = {
    autoIncrement()

    bind[TestUser].on(e => declare(
      e.properties is depends("user"),
      e.friends is eleColumn("friend_user_id"),
      e.tags is (table("users_tags"), keyLength(30), eleColumn("value2"), eleLength(200))))

    bind[TestRole].on(e => declare(
      e.name is unique,
      e.vocations is (joinColumn("role_id"), eleColumn("exclude_on")),
      e.properties is keyColumn("type_id")))

    bind[UserProperty]

    bind[UrlMenu].on(c => declare(
      c.url is (notnull, length(40)),
      c.parent is target[UrlMenu])).cacheAll()
  }
}
