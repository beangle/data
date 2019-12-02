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
package org.beangle.data.jdbc.dialect

import org.beangle.data.jdbc.engine.Engines

class OracleDialect extends AbstractDialect(Engines.Oracle) {

  options.sequence.createSql = "create sequence :name increment by :increment start with :start cache :cache :cycle"
  options.sequence.nextValSql = "select :name.nextval from dual"
  options.sequence.selectNextValSql = ":name.nextval"

  options.comment.supportsCommentOn = true

  /** limit offset
    * FIXME distinguish sql with order by or not
    *
    * @see http://blog.csdn.net/czp11210/article/details/23958065
    */
  override def limit(querySql: String, offset: Int, limit: Int): (String, List[Int]) = {
    var sql = querySql.trim()
    var isForUpdate = false
    if (sql.toLowerCase().endsWith(" for update")) {
      sql = sql.substring(0, sql.length - 11)
      isForUpdate = true
    }
    val pagingSelect = new StringBuilder(sql.length + 100)
    val hasOffset = offset > 0
    if (hasOffset) pagingSelect.append("select * from ( select row_.*, rownum rownum_ from ( ")
    else pagingSelect.append("select * from ( ")

    pagingSelect.append(sql)
    if (hasOffset) pagingSelect.append(" ) row_ where rownum <= ?) where rownum_ > ?")
    else pagingSelect.append(" ) where rownum <= ?")

    if (isForUpdate) pagingSelect.append(" for update")
    (pagingSelect.toString, if (hasOffset) List(limit + offset, offset) else List(limit))
  }

}
