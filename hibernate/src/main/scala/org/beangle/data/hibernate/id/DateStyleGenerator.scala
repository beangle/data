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

import java.sql.CallableStatement
import java.{ util => ju }

import org.beangle.commons.lang.JLong
import org.beangle.data.model.pojo.YearId
import org.hibernate.`type`.{ IntegerType, LongType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.jdbc.spi.JdbcCoordinator
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.mapping.Table
import org.hibernate.jdbc.AbstractReturningWork
import java.sql.Connection
import org.hibernate.resource.transaction.spi.TransactionCoordinator
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.service.ServiceRegistry
import org.beangle.data.hibernate.cfg.MappingService
import org.beangle.commons.lang.Strings

/**
 * Id generator based on function or procedure
 */
class DateStyleGenerator extends IdentifierGenerator with Configurable {

  var func: IdFunctor = _

  override def configure(t: Type, params: ju.Properties, serviceRegistry: ServiceRegistry) {
    t match {
      case longType: LongType =>
        func = LongIdFunctor
      case intType: IntegerType =>
        val em = serviceRegistry.getService(classOf[MappingService]).mappings.entityMappings(params.getProperty(IdentifierGenerator.ENTITY_NAME))
        val ownerSchema = em.table.schema.name.toString
        val schema = if (Strings.isEmpty(ownerSchema)) params.getProperty(SCHEMA) else ownerSchema

        val tableName = Table.qualify(params.getProperty(CATALOG), schema, params.getProperty(TABLE))
        func = new IntYearIdFunctor(tableName)
    }
  }

  override def generate(session: SharedSessionContractImplementor, obj: Object): java.io.Serializable = {
    val year = obj match {
      case yearObj: YearId => yearObj.year
      case _               => ju.Calendar.getInstance().get(ju.Calendar.YEAR)
    }
    func.gen(session.getTransactionCoordinator, year)
  }
}

abstract class IdFunctor {
  def gen(jdbc: TransactionCoordinator, year: Int): Number
}

object LongIdFunctor extends IdFunctor {
  val sql = "{? = call next_year_id(?)}"
  def gen(jdbc: TransactionCoordinator, year: Int): Number = {
    jdbc.createIsolationDelegate().delegateWork(
      new AbstractReturningWork[Number]() {
        def execute(connection: Connection): Number = {
          val st = connection.prepareCall(sql)
          try {
            st.registerOutParameter(1, java.sql.Types.BIGINT)
            st.setInt(2, year)
            st.execute()
            val id = new JLong(st.getLong(1))
            id
          } finally {
            st.close()
          }
        }
      }, true)
  }
}

class IntYearIdFunctor(tableName: String) extends IdFunctor {
  val sql = "{? = call next_id(?,?)}"

  def gen(jdbc: TransactionCoordinator, year: Int): Number = {
    jdbc.createIsolationDelegate().delegateWork(
      new AbstractReturningWork[Number]() {
        def execute(connection: Connection): Number = {
          val st = connection.prepareCall(sql)
          try {
            st.registerOutParameter(1, java.sql.Types.BIGINT)
            st.setString(2, tableName)
            st.setInt(3, year)
            st.execute()
            val id = Integer.valueOf(st.getLong(1).asInstanceOf[Int])
            id
          } finally {
            st.close()
          }
        }
      }, true)
  }
}
