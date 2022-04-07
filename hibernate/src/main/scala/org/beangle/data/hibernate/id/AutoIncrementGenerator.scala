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

package org.beangle.data.hibernate.id

import java.sql.Connection
import java.{util => ju}

import org.beangle.commons.lang.Strings
import org.beangle.data.hibernate.cfg.MappingService
import org.beangle.data.jdbc.meta.Table
import org.hibernate.`type`.{IntegerType, LongType, ShortType, Type}
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.PersistentIdentifierGenerator.{SCHEMA, TABLE}
import org.hibernate.id.{Configurable, IdentifierGenerator}
import org.hibernate.jdbc.AbstractReturningWork
import org.hibernate.service.ServiceRegistry

class AutoIncrementGenerator extends IdentifierGenerator {
  var identifierType: Type = _
  val sql = "{? = call next_id(?)}"
  var tableName: String = _

  override def configure(t: Type, params: ju.Properties, serviceRegistry: ServiceRegistry): Unit = {
    this.identifierType = t
    val em = serviceRegistry.getService(classOf[MappingService]).mappings.entityTypes(params.getProperty(IdentifierGenerator.ENTITY_NAME))
    val ownerSchema = em.table.schema.name.toString
    val schema = if (Strings.isEmpty(ownerSchema)) params.getProperty(SCHEMA) else ownerSchema
    tableName = Table.qualify(schema, params.getProperty(TABLE)).toLowerCase()
  }

  def generate(session: SharedSessionContractImplementor, obj: Object): java.io.Serializable = {
    session.getTransactionCoordinator.createIsolationDelegate().delegateWork(
      new AbstractReturningWork[Number]() {
        def execute(connection: Connection): Number = {
          val st = connection.prepareCall(sql)
          try {
            st.registerOutParameter(1, java.sql.Types.BIGINT)
            st.setString(2, tableName)
            st.execute()
            val id = java.lang.Long.valueOf(st.getLong(1))
            identifierType match {
              case _: LongType => id
              case _: IntegerType => Integer.valueOf(id.intValue())
              case _: ShortType => java.lang.Short.valueOf(id.shortValue())
            }
          } finally {
            st.close()
          }
        }
      }, true)
  }
}
