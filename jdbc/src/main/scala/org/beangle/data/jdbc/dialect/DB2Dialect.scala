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

class DB2Dialect extends AbstractDialect(Engines.DB2) {

  options.sequence.nextValSql = "values nextval for :name"
  options.sequence.dropSql = "drop sequence :name restrict"
  options.sequence.selectNextValSql = "nextval for :name"
  options.comment.supportsCommentOn = true

  override def limit(sql: String, offset: Int, limit: Int): (String, List[Int]) = {
    if (offset == 0) {
      (sql + " fetch first " + limit + " rows only", List.empty)
    } else {
      //nest the main query in an outer select
      ("select * from ( select inner2_.*, rownumber() over(order by order of inner2_) as rownumber_ from ( "
        + sql + " fetch first " + limit + " rows only ) as inner2_ ) as inner1_ where rownumber_ > "
        + offset + " order by rownumber_", List.empty)
    }
  }
}
