/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.model.dao

object Query {
  case class Lang(val name: String)
  val hql = new Lang("hql")
  val sql = new Lang("sql")
}
import Query._
/**
 * 数据查询接口
 *
 * @author chaostone
 */
trait Query[T] {

  /**
   * Returns query statement.
   */
  def statement: String

  /**
   * getParams.
   */
  def params: Map[String, Any]

  /**
   * <p>
   * isCacheable.
   * </p>
   *
   * @return a boolean.
   */
  def cacheable: Boolean

  /**
   * <p>
   * getLang.
   * </p>
   *
   * @return a {@link org.beangle.commons.dao.query.Lang} object.
   */
  def lang: Lang
}

