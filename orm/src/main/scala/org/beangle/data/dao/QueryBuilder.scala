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

import org.beangle.commons.collection.page.PageLimit

/** QueryBuilder interface
 *
 * @author chaostone
 */
trait QueryBuilder[T] {

  /**
   * build.
   */
  def build(): Query[T]

  /**
   * limit.
   */
  def limit(limit: PageLimit): QueryBuilder[T]

  /**
   * getParams.
   */
  def params: collection.Map[String, Any]

  /**
   * params.
   */
  def params(newParams: collection.Map[String, Any]): this.type
}
