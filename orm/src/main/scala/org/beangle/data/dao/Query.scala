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

package org.beangle.data.dao

object Query {
  enum Lang(name: String) {
    case OQL extends Lang("oql")
    case SQL extends Lang("sql")
  }
}

import org.beangle.data.dao.Query.*

/**
 * 数据查询接口
 *
 * @author chaostone
 */
trait Query[T] {

  def statement: String

  def params: Map[String, Any]

  def cacheable: Boolean

  def lang: Lang
}
