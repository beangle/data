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

import org.beangle.commons.lang.{JLong, Strings}
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.orm.hibernate.cfg.MappingService
import org.hibernate.`type`.Type
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.PersistentIdentifierGenerator.{SCHEMA, TABLE}
import org.hibernate.id.{Configurable, IdentifierGenerator}
import org.hibernate.jdbc.AbstractReturningWork
import org.hibernate.service.ServiceRegistry

import java.sql.Connection
import java.time.LocalDate
import java.util as ju

/**
 * Id generator based on function or procedure,
 * format:
 *
 * {{{
 *      id_function()
 * }}}
 *
 * default function is date_id
 */
abstract class AbstractSqlStyleGenerator extends IdentifierGenerator {

  protected def sql: String

  override def generate(session: SharedSessionContractImplementor, obj: Object): java.io.Serializable = {
    session.getTransactionCoordinator().createIsolationDelegate().delegateWork(
      new AbstractReturningWork[Number]() {
        def execute(connection: Connection): Number = {
          val st = connection.prepareCall(sql)
          try {
            st.registerOutParameter(1, java.sql.Types.BIGINT)
            st.execute()
            java.lang.Long.valueOf(st.getLong(1))
          } finally {
            st.close()
          }
        }
      }, true)
  }
}
