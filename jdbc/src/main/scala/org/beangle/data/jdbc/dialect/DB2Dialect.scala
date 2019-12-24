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

  options.sequence { s =>
    s.nextValSql = "values nextval for {name}"
    s.dropSql = "drop sequence {name} restrict"
    s.selectNextValSql = "nextval for {name}"
  }
  options.comment.supportsCommentOn = true

  // 和 postgresql 比较接近
  options.alter { a =>
    a.table.addColumn = "add {column} {type}"
    a.table.changeType = "alter column {column} set data type {type}"
    a.table.setDefault = "alter column {column} set default {value}"
    a.table.dropDefault = "alter column {column} drop default"
    a.table.setNotNull = "alter column {column} set not null"
    a.table.dropNotNull = "alter column {column} drop not null"
    a.table.dropColumn = "drop column {column}"

    a.table.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.table.dropConstraint = "drop constraint {name}"
  }
  options.validate()

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
