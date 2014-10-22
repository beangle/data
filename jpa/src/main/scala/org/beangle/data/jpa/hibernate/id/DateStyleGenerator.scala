package org.beangle.data.jpa.hibernate.id

import java.text.SimpleDateFormat
import java.{ util => ju }

import org.beangle.data.model.{ FasterId, SlowId, YearId }
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.IdentifierGenerator

/**
 * Id generator based on function or procedure
 */
class DateStyleGenerator extends IdentifierGenerator {

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    var year = 0
    val func = if (obj.isInstanceOf[YearId]) {
      year = obj.asInstanceOf[YearId].year
      val curYear = ju.Calendar.getInstance().get(ju.Calendar.YEAR)
      obj match {
        case fastr: FasterId => if (year == curYear) LongDateId else LongYearId
        case slow: SlowId => IntYearId
        case _ => if (year == curYear) LongSecondId else LongYearId
      }
    } else {
      year = ju.Calendar.getInstance().get(ju.Calendar.YEAR)
      obj match {
        case faster: FasterId => LongDateId
        case slow: SlowId => IntYearId
        case _ => LongSecondId
      }
    }
    val jdbcCoordinator = session.getTransactionCoordinator().getJdbcCoordinator()
    val st = jdbcCoordinator.getStatementPreparer().prepareStatement(session.getFactory().getDialect().getSequenceNextValString(func.sequence))
    try {
      val rs = jdbcCoordinator.getResultSetReturn().extract(st)
      rs.next()
      val id = func.gen(year, rs.getLong(1))
      jdbcCoordinator.release(rs, st)
      id
    } finally {
      jdbcCoordinator.release(st)
    }
  }
}
abstract class IdFunc(val sequence: String) {
  def gen(year: Int = 0, seq: Number): Number
}

object LongSecondId extends IdFunc("seq_second4") {
  val format = new SimpleDateFormat("YYYYMMDDHHmmss")
  override def gen(year: Int, seq: Number): Number = {
    val cal = ju.Calendar.getInstance
    java.lang.Long.valueOf(format.format(new ju.Date)) * 10000 + seq.intValue
  }
}

object LongDateId extends IdFunc("seq_day10") {
  val format = new SimpleDateFormat("YYYYMMDD")
  val base = Math.pow(10, 10).asInstanceOf[Long]
  override def gen(year: Int, seq: Number): Number = {
    val cal = ju.Calendar.getInstance
    java.lang.Long.valueOf(format.format(new ju.Date)) * base + seq.longValue
  }
}

object LongYearId extends IdFunc("seq_year14") {
  val base = Math.pow(10, 14).asInstanceOf[Long]
  override def gen(year: Int, seq: Number): Number = {
    year * base + seq.longValue
  }
}

object IntYearId extends IdFunc("seq_year5") {
  override def gen(year: Int, seq: Number): Number = {
    year * 100000 + seq.intValue
  }
}

