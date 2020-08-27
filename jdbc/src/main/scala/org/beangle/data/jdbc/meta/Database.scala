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
package org.beangle.data.jdbc.meta

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.engine.Engine

class Database(val engine: Engine) {

  var schemas = new collection.mutable.HashMap[Identifier, Schema]

  def getOrCreateSchema(schema: Identifier): Schema = {
    schemas.getOrElseUpdate(schema, new Schema(this, schema))
  }

  def getTable(schema: String, name: String): Option[Table] = {
    getOrCreateSchema(schema).getTable(name)
  }

  def addTable(schemaName: String, tableName: String): Table = {
    val schema = getOrCreateSchema(schemaName)
    val table = new Table(schema, engine.toIdentifier(tableName))
    schema.addTable(table)
    table
  }

  def addTable(schema: String, table: Table): Table = {
    getOrCreateSchema(schema).addTable(table)
    table
  }

  def hasQuotedIdentifier: Boolean = {
    schemas.exists(_._2.hasQuotedIdentifier)
  }

  def refTable(tableQualifier: String): TableRef = {
    var referSchemaName = ""
    var referTableName = tableQualifier
    if (tableQualifier.contains(".")) {
      referSchemaName = Strings.substringBefore(tableQualifier, ".")
      referTableName = Strings.substringAfter(tableQualifier, ".")
    }
    val referSchema = this.getOrCreateSchema(referSchemaName)
    TableRef(referSchema, engine.toIdentifier(referTableName))
  }

  def getOrCreateSchema(schema: String): Schema = {
    if (Strings.isEmpty(schema)) {
      getOrCreateSchema(Identifier.empty)
    } else {
      getOrCreateSchema(engine.toIdentifier(schema))
    }
  }
}
