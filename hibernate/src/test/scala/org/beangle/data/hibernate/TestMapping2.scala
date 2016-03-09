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
package org.beangle.data.hibernate

import scala.reflect.runtime.universe

import org.beangle.commons.lang.annotation.beta
import org.beangle.data.hibernate.model.{ CodedEntity, Department, ExtendRole, Menu, Role, StringIdCodedEntity }
import org.beangle.data.model.bind.Mapping

object TestMapping2 extends Mapping {

  def binding(): Unit = {
    defaultIdGenerator("table_sequence")
    defaultCache("test_cache_region", "read-write")

    bind[Role].on(r => declare(
      r.name is (notnull, length(112), unique),
      r.children is (depends("parent"), cacheable))).generator("assigned")

    bind[ExtendRole](classOf[Role].getName)

    bind[CodedEntity].on(c => declare(
      c.code is (length(22))))

    bind[StringIdCodedEntity].on(c => declare(
      c.code is (length(28)))).generator("native")

    bind[Menu]
    cache().add(collection[Role]("children"))

    bind[Department].on(e => declare(
      e.children is (one2many("parent"))))
  }

}