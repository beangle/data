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

import org.beangle.commons.collection.page.{Page, PageLimit, SinglePage}
import org.beangle.data.model.Entity

object QueryPage {
  def apply[T <: Entity[_]](query: OqlBuilder[T], entityDao: EntityDao): QueryPage[T] = {
    if null == query.limit then query.limit(1, 100)
    val q = query.build().asInstanceOf[LimitQuery[T]]
    new QueryPage[T](q, entityDao)
  }
}

/**
 * QueryPage class.
 *
 * @author chaostone
 */
class QueryPage[T <: Entity[_]](query: LimitQuery[T], val entityDao: EntityDao) extends AbstractQueryPage[T](query) {

  next()

  def moveTo(pageIndex: Int): Page[T] = {
    query.limit(PageLimit(pageIndex, query.limit.pageSize))
    updatePage(entityDao.search(query).asInstanceOf[SinglePage[T]])
    this
  }
}
