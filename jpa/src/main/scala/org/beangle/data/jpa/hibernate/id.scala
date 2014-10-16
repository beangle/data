/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.jpa.hibernate;

import java.text.SimpleDateFormat
import java.{ util => ju }

import org.beangle.commons.lang.{ Chars, Numbers, Strings }
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.mapping.NamingPolicy
import org.beangle.data.model.{ Coded, IdGrowFastest, IdGrowSlow, YearId }
import org.hibernate.`type`.{ IntegerType, LongType, ShortType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, SCHEMA, TABLE }
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.id.enhanced.SequenceStyleGenerator.{ DEF_SEQUENCE_NAME, SEQUENCE_PARAM }
import org.hibernate.mapping.Table

/**
 * 按照表明进行命名序列<br>
 * 依据命名模式进行，默认模式seq_{table}<br>
 * 该生成器可以
 *
 * <pre>
 * 1)具有较好的数据库移植性，支持没有sequence的数据库。
 * 2)可以通过设置优化起进行优化
 * 3)可以按照表名进行自动命名序列名，模式seq_{table}
 * </pre>
 *
 * @author chaostone
 */
class TableSeqGenerator extends SequenceStyleGenerator with Logging {

  var sequencePrefix = "seq_"

  protected override def determineSequenceName(params: ju.Properties, dialect: Dialect): String = {
    import SequenceStyleGenerator._
    var seqName = params.getProperty(SEQUENCE_PARAM)
    if (Strings.isEmpty(seqName)) {
      val tableName = params.getProperty(TABLE)
      seqName = if (null != tableName) sequencePrefix + tableName else DEF_SEQUENCE_NAME
    }

    if (seqName.indexOf('.') < 0) {
      val entityName = params.getProperty(IdentifierGenerator.ENTITY_NAME)
      if (null != entityName && null != NamingPolicy.Instance) {
        val schema = NamingPolicy.Instance.getSchema(entityName).getOrElse(params.getProperty(SCHEMA))
        seqName = Table.qualify(dialect.quote(params.getProperty(CATALOG)), dialect.quote(schema), dialect.quote(seqName))
      }
    }
    if (Strings.substringAfterLast(seqName, ".").length > NamingPolicy.DefaultMaxLength) warn(s"$seqName's length >=30, wouldn't be supported in oracle!")
    seqName
  }
}

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
        case fastest: IdGrowFastest => if (year == curYear) LongDateId else LongYearId
        case slow: IdGrowSlow => IntYearId
        case _ => if (year == curYear) LongSecondId else LongYearId
      }
    } else {
      year = ju.Calendar.getInstance().get(ju.Calendar.YEAR)
      obj match {
        case fastest: IdGrowFastest => LongDateId
        case slow: IdGrowSlow => IntYearId
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

/**
 * Id generator based on function or procedure
 */
class CodeStyleGenerator extends IdentifierGenerator with Configurable {
  var identifierType: Type = _
  override def configure(t: Type, params: ju.Properties, dialect: Dialect) {
    this.identifierType = t;
  }

  def generate(session: SessionImplementor, obj: Object): java.io.Serializable = {
    obj match {
      case coded: Coded =>
        var result = identifierType match {
          case lt: LongType => Numbers.convert2Long(coded.code, null)
          case it: IntegerType => Numbers.convert2Int(coded.code, null)
          case st: ShortType => Numbers.convert2Short(coded.code, null)
        }
        if (null == result) {
          val code = coded.code
          val builder = new StringBuilder
          for (i <- 0 until code.length) {
            val ch = code.charAt(i)
            if (Chars.isAsciiAlpha(ch)) {
              builder ++= String.valueOf((Character.toLowerCase(ch.asInstanceOf[Int]) - 'a'.asInstanceOf[Int] + 10))
            } else {
              builder ++= String.valueOf(ch)
            }
          }
          result = identifierType match {
            case lt: LongType => Numbers.convert2Long(builder.toString)
            case it: IntegerType => Numbers.convert2Int(builder.toString)
            case st: ShortType => Numbers.convert2Short(builder.toString)
          }
        }
        result
      case _ => throw new RuntimeException("CodedIdGenerator only support Coded")
    }
  }
}

