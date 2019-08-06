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

/** SqlBuilder
  * @author chaostone
  */
object SqlBuilder {

  def sql(queryStr: String): SqlBuilder = {
    val sqlQuery = new SqlBuilder()
    sqlQuery.statement = queryStr
    sqlQuery
  }

  val Lang = Query.Lang("Sql")
}

class SqlBuilder extends AbstractQueryBuilder[Array[Any]] {

  protected def genCountStatement(): String = {
    "select count(*) from (" + genQueryStatement(false) + ")"
  }

  override def lang: Query.Lang = SqlBuilder.Lang
}
