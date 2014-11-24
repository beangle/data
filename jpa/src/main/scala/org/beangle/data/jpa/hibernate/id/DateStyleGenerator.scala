package org.beangle.data.jpa.hibernate.id

import java.text.SimpleDateFormat
import java.{ util => ju }

import org.beangle.commons.lang.JLong
import org.beangle.data.jpa.mapping.NamingPolicy
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
        func = new LongIdFunctor(dialect.getSequenceNextValString("seq_minute6"))
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
    val jdbc = session.getTransactionCoordinator().getJdbcCoordinator()
    func.gen(jdbc, year)
  }
}

abstract class IdFunctor {
  def gen(jdbc: JdbcCoordinator, year: Int): Number
}

class LongIdFunctor(sql: String) extends IdFunctor {
  val minuteFormat = new SimpleDateFormat("yyyyMMddHHmm")
  val dateFormat = new SimpleDateFormat("yyyyMMdd")

  def gen(jdbc: JdbcCoordinator, year: Int): Number = {
    val st = jdbc.getStatementPreparer().prepareStatement(sql)
    try {
      val rs = jdbc.getResultSetReturn().extract(st)
      rs.next()
      val id = format(year, rs.getLong(1))
      jdbc.release(rs, st)
      id
    } finally {
      jdbc.release(st)
    }
  }

  def format(year: Int, seq: Long): Number = {
    val cal = ju.Calendar.getInstance
    val curYear = cal.get(ju.Calendar.YEAR)
    if (year == curYear) {
      new JLong(java.lang.Long.parseLong(minuteFormat.format(new ju.Date)) * 1000000 + seq)
    } else {
      new JLong(java.lang.Long.parseLong(String.valueOf(year) + dateFormat.format(new ju.Date)) * 1000000 + seq)
    }
  }
}

class IntYearIdFunctor(tableName: String) extends IdFunctor {
  val sql = "next_year_id(?,?)"
  def gen(jdbc: JdbcCoordinator, year: Int): Number = {
    val st = jdbc.getStatementPreparer().prepareStatement(sql, true)
    try {
      st.setString(1, tableName)
      st.setInt(2, year)
      val rs = jdbc.getResultSetReturn().extract(st)
      rs.next()
      val id = Integer.valueOf(rs.getInt(1))
      jdbc.release(rs, st)
      id
    } finally {
      jdbc.release(st)
    }
  }
}
