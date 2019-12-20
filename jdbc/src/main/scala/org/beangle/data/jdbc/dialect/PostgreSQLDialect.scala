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

class PostgreSQLDialect extends AbstractDialect(Engines.PostgreSQL) {

  options.sequence { s =>
    s.nextValSql = "select nextval ('{name}')"
    s.selectNextValSql = "nextval ('{name}')"
  }

  options.comment.supportsCommentOn = true
  options.limit { l =>
    l.pattern = "{} limit ?"
    l.offsetPattern = "{} limit ? offset ?"
    l.bindInReverseOrder = true
  }

  options.drop.table.sql = "drop table {name} cascade"

  options.alter { a =>
    a.table.changeType = "alter {column} type {type}"
    a.table.setDefault="alter {column} set default {value}"
    a.table.dropDefault="alter {column} drop default"
    a.table.setNotNull = "alter {column} set not null"
    a.table.dropNotNull = "alter {column} drop not null"
    a.table.addColumn = "add {column} {type}"
    a.table.dropColumn = "drop {column} cascade"

    a.table.addPrimaryKey="add constraint {name} primary key ({column-list})"
    a.table.dropConstraint="drop constraint {name} cascade"
  }
  options.validate()
}
