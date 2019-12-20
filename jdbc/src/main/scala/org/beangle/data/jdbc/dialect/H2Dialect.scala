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

class H2Dialect extends AbstractDialect(Engines.H2) {
  options.sequence { s =>
    s.nextValSql = "call next value for {name}"
    s.selectNextValSql = "next value for {name}"
    s.createSql = "create sequence {name} start with {start} increment by {increment} cache {cache}"
    s.dropSql = "drop sequence if exists {name}"
  }

  options.limit.pattern = "{} limit ?"
  options.limit.offsetPattern = "{} limit ? offset ?"
  options.limit.bindInReverseOrder = true
  options.comment.supportsCommentOn = true

  options.alter { a =>
    a.table.changeType = "alter {column} {type}"
    a.table.setDefault="alter {column} set default {value}"
    a.table.dropDefault="alter {column} set default null"
    a.table.setNotNull = "alter {column} set not null"
    a.table.dropNotNull = "alter {column} set null"
    a.table.addColumn = "add {column} {type}"
    a.table.dropColumn = "drop column {column}"

    a.table.addPrimaryKey="add constraint {name} primary key ({column-list})"
    a.table.dropConstraint="drop constraint {name}"
  }

  options.validate()
}
