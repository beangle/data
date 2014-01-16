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
package org.beangle.data.jdbc.meta

import scala.collection.mutable
import java.sql.{ SQLException, DatabaseMetaData }
import org.beangle.data.jdbc.dialect.Dialect

/**
 * JDBC database metadata
 *
 * @author chaostone
 */
class Database(meta: DatabaseMetaData, val dialect: Dialect, val catalog: String, val schema: String) {

  val tables = if (meta.storesMixedCaseIdentifiers) new mutable.HashMap[String, Table] else new CaseInsensitiveMap[Table]

  val sequences = new mutable.HashSet[Sequence]

  def loadTables(extras: Boolean): mutable.HashMap[String, Table] = {
    val loader = new MetadataLoader(dialect, meta)
    val loadTables: Set[Table] = loader.loadTables(catalog, schema, extras)
    for (table <- loadTables) {
      tables.put(table.identifier, table)
    }
    tables
  }

  def loadSequences(): mutable.HashSet[Sequence] = {
    sequences ++= new MetadataLoader(dialect, meta).loadSequences(meta.getConnection(), schema)
    sequences
  }

  def getTable(name: String) = tables.get(name)

  override def toString = "Database" + tables.keySet.toString() + sequences.toString()
}

class CaseInsensitiveMap[V] extends mutable.HashMap[String, V] {
  override def put(key: String, value: V) = super.put(key.toLowerCase, value)

  override def get(key: String): Option[V] = return super.get(key.toString.toLowerCase)

  override def remove(key: String): Option[V] = { return super.remove(key.toString().toLowerCase) }
}
