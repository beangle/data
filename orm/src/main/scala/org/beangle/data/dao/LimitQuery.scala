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

import org.beangle.commons.collection.page.PageLimit

/** LimitQuery interface.
  * @author chaostone
  */
trait LimitQuery[T] extends Query[T] {

  /** getLimit
    * @return a { @link org.beangle.commons.collection.page.PageLimit} object.
    */
  def limit: PageLimit

  /** Set limit
    * @param limit a { @link org.beangle.commons.collection.page.PageLimit} object.
    * @return a { @link org.beangle.data.dao.query.LimitQuery} object.
    */
  def limit(limit: PageLimit): LimitQuery[T]

  /** getCountQuery
    * @return a { @link org.beangle.data.dao.query.Query} object.
    */
  def countQuery: Query[T]
}
