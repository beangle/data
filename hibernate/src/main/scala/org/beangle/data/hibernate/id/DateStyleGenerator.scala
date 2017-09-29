/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate.id

import java.sql.Connection
import java.time.LocalDate
import java.{ util => ju }

import org.beangle.commons.lang.{ JLong, Strings }
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.hibernate.cfg.MappingService
import org.hibernate.`type`.{ LongType, Type }
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ SCHEMA, TABLE }
import org.hibernate.jdbc.AbstractReturningWork
import org.hibernate.service.ServiceRegistry

/**
 * Id generator based on function or procedure,
 * format:
 * 
 *   {{{
 *      prefix YYYYMMDD sequence
 *   }}}
 * 
 *   default function is date_id$seqLength
 */
class DateStyleGenerator extends IdentifierGenerator with Configurable {

  var prefix: String = ""

  private var dateIdFunc:String = _

  private var tableName: String = _

  private var sql: String = _

  private val format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")

  private var seqLength: Int = _

  override def configure(t: Type, params: ju.Properties, serviceRegistry: ServiceRegistry) {
    t match {
      case longType: LongType =>
        val em = serviceRegistry.getService(classOf[MappingService]).mappings.entityMappings(params.getProperty(IdentifierGenerator.ENTITY_NAME))
        val ownerSchema = em.table.schema.name.toString
        val schema = if (Strings.isEmpty(ownerSchema)) params.getProperty(SCHEMA) else ownerSchema
        tableName = Table.qualify(schema, params.getProperty(TABLE))

        prefix = params.getProperty("prefix")
        if (prefix == null) prefix = ""
        seqLength = 19 - 8 - prefix.length

        dateIdFunc = s"date_id${seqLength}"
        sql = s"{? = call $dateIdFunc(?,?)"
      case _ =>
        throw new RuntimeException("DateStyleGenerator only support long type id")
    }
  }

  override def generate(session: SharedSessionContractImplementor, obj: Object): java.io.Serializable = {
    session.getTransactionCoordinator().createIsolationDelegate().delegateWork(
      new AbstractReturningWork[Number]() {
        def execute(connection: Connection): Number = {
          val st = connection.prepareCall(sql)
          try {
            st.registerOutParameter(1, java.sql.Types.BIGINT)
            st.setString(2, prefix)
            st.setString(3, tableName)
            st.execute()
            val today = LocalDate.now
            val id = st.getLong(1)
            new JLong(prefix + today.format(format) + Strings.leftPad(id.toString, seqLength, '0'))
          } finally {
            st.close()
          }
        }
      }, true)
  }
}

