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
package org.beangle.data.jdbc.migration

import org.beangle.data.jdbc.dialect.Dialect

class Migrator {

  def sql(diff: DatabaseDiff): String = {
    if (diff.isEmpty) return ""

    val sb = new StringBuilder
    val engine = diff.newer.engine
    val dialect: Dialect = null

    diff.schemas.newer foreach { n =>
      sb ++= s"""create schema $n;"""
    }
    diff.schemas.removed foreach { n =>
      sb ++= s"DROP schema $n cascade;"
    }
    diff.schemaDiffs foreach { case (schema, sdf) =>
      sdf.tables.newer foreach { t =>
        sb ++= dialect.createTable(diff.newer.getTable(schema, t).get)
      }
      sdf.tables.removed foreach { t =>
        sb ++= dialect.dropTable(diff.older.getTable(schema, t).get.qualifiedName)
      }
      sdf.tableDiffs foreach { case (t, tdf) =>
        tdf.columns.newer foreach { c =>
          dialect.alterTableAddColumn(tdf.newer, tdf.newer.column(c))
        }
        tdf.columns.removed foreach { c =>
          dialect.alterTableDropColumn(tdf.older, tdf.older.column(c))
        }
        tdf.columns.updated foreach { c =>
          val newColumn = tdf.newer.column(c)
          val oldColumn = tdf.older.column(c)

        }
      }
    }
    sb.mkString
  }

}