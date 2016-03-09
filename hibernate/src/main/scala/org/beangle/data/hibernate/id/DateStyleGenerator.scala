/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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
import org.beangle.data.hibernate.naming.NamingPolicy
import org.beangle.data.model.YearId
import org.hibernate.`type`.{ IntegerType, LongType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.jdbc.spi.JdbcCoordinator
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.mapping.Table

/**
 * Id generator based on function or procedure
 */
class DateStyleGenerator extends IdentifierGenerator with Configurable {

  var func: IdFunctor = _

  override def configure(t: Type, params: ju.Properties, dialect: Dialect) {
    t match {
      case longType: LongType =>
        func = LongIdFunctor
      case intType: IntegerType =>
        val schema = NamingPolicy.Instance.getSchema(params.getProperty(IdentifierGenerator.ENTITY_NAME)).getOrElse(params.getProperty(SCHEMA))
        val tableName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(params.getProperty(TABLE)))
        func = new IntYearIdFunctor(tableName)
    }
  }

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    val year = obj match {
      case yearObj: YearId => yearObj.year
      case _               => ju.Calendar.getInstance().get(ju.Calendar.YEAR)
    }
    func.gen(session.getTransactionCoordinator.getJdbcCoordinator, year)
  }
}

abstract class IdFunctor {
  def gen(jdbc: JdbcCoordinator, year: Int): Number
}

object LongIdFunctor extends IdFunctor {
  val sql = "{? = call next_year_id(?)}"

  def gen(jdbc: JdbcCoordinator, year: Int): Number = {
    val st = jdbc.getStatementPreparer().prepareStatement(sql, true).asInstanceOf[CallableStatement]
    try {
      st.registerOutParameter(1, java.sql.Types.BIGINT)
      st.setInt(2, year)
      st.execute()
      val id = new JLong(st.getLong(1))
      id
    } finally {
      jdbc.release(st)
    }
  }
}

class IntYearIdFunctor(tableName: String) extends IdFunctor {
  val sql = "{? = call next_id(?,?)}"

  def gen(jdbc: JdbcCoordinator, year: Int): Number = {
    val st = jdbc.getStatementPreparer().prepareStatement(sql, true).asInstanceOf[CallableStatement]
    try {
      st.registerOutParameter(1, java.sql.Types.BIGINT)
      st.setString(2, tableName)
      st.setInt(3, year)
      st.execute()
      val id = Integer.valueOf(st.getLong(1).asInstanceOf[Int])
      id
    } finally {
      jdbc.release(st)
    }
  }
}
