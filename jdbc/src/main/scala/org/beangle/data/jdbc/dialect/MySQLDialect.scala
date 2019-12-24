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

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.engine.Engines
import org.beangle.data.jdbc.meta.{PrimaryKey, Table}

class MySQLDialect extends AbstractDialect(Engines.MySQL) {

  options.sequence.supports = false
  options.alter { a =>
    a.table.addColumn = "add {column} {type}"
    a.table.changeType = "modify column {column} {type}"
    a.table.setDefault = "alter {column} set default {value}"
    a.table.dropDefault = "alter {column} drop default"
    a.table.setNotNull = "modify {column} {type} not null"
    a.table.dropNotNull = "modify {column} {type}"
    a.table.dropColumn = "drop column {column}"

    a.table.addPrimaryKey = "add primary key ({column-list})"
    a.table.dropConstraint = "drop constraint {name}"
  }

  options.limit.pattern = "{} limit ?"
  options.limit.offsetPattern = "{} limit ? offset ?"
  options.limit.bindInReverseOrder = true

  options.comment.supportsCommentOn = false

  override def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
                             referencedTable: String, primaryKey: Iterable[String]): String = {
    val cols = Strings.join(foreignKey, ", ")
    new StringBuffer(30).append(" add index ").append(constraintName).append(" (").append(cols)
      .append("), add constraInt ").append(constraintName).append(" foreign key (").append(cols)
      .append(") references ").append(referencedTable).append(" (")
      .append(Strings.join(primaryKey, ", ")).append(')').toString
  }

  override def alterTableDropPrimaryKey(table: Table, pk: PrimaryKey): String = {
    s"alter table ${table.qualifiedName}  drop primary key"
  }

}
