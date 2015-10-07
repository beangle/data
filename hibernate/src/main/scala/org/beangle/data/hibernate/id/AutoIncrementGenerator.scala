/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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

import java.{ util => ju }
import org.beangle.data.hibernate.naming.NamingPolicy
import org.hibernate.`type`.{ IntegerType, LongType, ShortType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.mapping.Table
import org.hibernate.engine.jdbc.spi.JdbcCoordinator
import java.sql.CallableStatement

class AutoIncrementGenerator extends IdentifierGenerator with Configurable {
  var identifierType: Type = _
  val sql = "{? = call next_id(?)}"
  var tableName: String = _

  override def configure(t: Type, params: ju.Properties, dialect: Dialect) {
    this.identifierType = t
    val schema = NamingPolicy.Instance.getSchema(params.getProperty(IdentifierGenerator.ENTITY_NAME)).getOrElse(params.getProperty(SCHEMA))
    tableName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(params.getProperty(TABLE)))
  }

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    val jdbc = session.getTransactionCoordinator.getJdbcCoordinator
    val st = jdbc.getStatementPreparer().prepareStatement(sql, true).asInstanceOf[CallableStatement]
    try {
      st.registerOutParameter(1, java.sql.Types.BIGINT)
      st.setString(2, tableName)
      st.execute()
      val id = java.lang.Long.valueOf(st.getLong(1))
      identifierType match {
        case lt: LongType    => id
        case it: IntegerType => Integer.valueOf(id.intValue())
        case sht: ShortType  => java.lang.Short.valueOf(id.shortValue())
      }
    } finally {
      jdbc.release(st)
    }

  }
}
