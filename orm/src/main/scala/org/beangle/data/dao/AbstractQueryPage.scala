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
package org.beangle.data.dao

import org.beangle.commons.collection.page._

/** 基于查询的分页
  * 当使用或导出大批量数据时，使用者仍以List的方式进行迭代。<br>
  * 该实现则是内部采用分页方式。
  *
  * @author chaostone
  */
abstract class AbstractQueryPage[T](val query: LimitQuery[T]) extends Page[T] {

  var page: Page[T] = _

  var pageIndex: Int = if (null != query.limit) query.limit.pageIndex - 1 else 0

  var totalPages = 0

  if (null == query.limit) query.limit(PageLimit(Page.DefaultPageNo, Page.DefaultPageSize))

  /** 按照单个分页数据设置.
    *
    * @param page a { @link org.beangle.commons.collection.page.SinglePage} object.
    */
  protected def updatePage(page: SinglePage[T]): Unit = {
    this.page = page
    this.pageIndex = page.pageIndex
    this.totalPages = page.totalPages
  }

  override def next(): Page[T] = moveTo(pageIndex + 1)

  override def previous(): Page[T] = moveTo(pageIndex - 1)

  override def hasNext: Boolean = totalPages > pageIndex

  override def hasPrevious: Boolean = pageIndex > 1

  override def pageSize: Int = query.limit.pageSize

  override def totalItems: Int = page.totalItems

  override def items: collection.Seq[T] = page.items

  override def length: Int = page.length

  override def apply(index: Int): T = page(index)

  override def iterator: Iterator[T] = new PageIterator[T](this)

}

class PageIterator[T](val queryPage: AbstractQueryPage[T]) extends Iterator[T] {

  private var dataIndex: Int = 0

  private var innerIter = queryPage.page.iterator

  override def hasNext: Boolean = (dataIndex < queryPage.page.items.size) || queryPage.hasNext

  override def next(): T = {
    if (dataIndex < queryPage.page.size) {
      dataIndex += 1
      innerIter.next()
    } else {
      queryPage.next()
      dataIndex = 1
      innerIter = queryPage.page.iterator
      innerIter.next()
    }
  }

}
