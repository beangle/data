/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.orm.hibernate.id

import org.beangle.commons.lang.Strings
import org.beangle.jdbc.meta.Table
import org.beangle.data.orm.hibernate.cfg.MappingService
import org.hibernate.id.IdentifierGenerator
import org.hibernate.id.PersistentIdentifierGenerator.{SCHEMA, TABLE}
import org.hibernate.service.ServiceRegistry

import java.util as ju

object IdHelper {

  def getTableQualifiedName(params: ju.Properties, registry: ServiceRegistry): String = {
    val em = registry.getService(classOf[MappingService]).mappings.entityTypes(params.getProperty(IdentifierGenerator.ENTITY_NAME))
    val ownerSchema = em.table.schema.name.toString
    val schema = if (Strings.isEmpty(ownerSchema)) params.getProperty(SCHEMA) else ownerSchema
    Table.qualify(schema, params.getProperty(TABLE)).toLowerCase()
  }

  def getEntityClass(params: ju.Properties, registry: ServiceRegistry): Class[_] = {
    val em = registry.getService(classOf[MappingService]).mappings.entityTypes(params.getProperty(IdentifierGenerator.ENTITY_NAME))
    em.clazz
  }

  def convertType(id: Long, idType: Class[_]): Number = {
    if idType == classOf[Long] then id
    else if idType == classOf[Int] then Integer.valueOf(id.intValue)
    else java.lang.Short.valueOf(id.shortValue)
  }
}
