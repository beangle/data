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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.{Chars, Numbers, Primitives}
import org.beangle.data.model.pojo.Coded
import org.hibernate.`type`.*
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.{SessionImplementor, SharedSessionContractImplementor}
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.{Configurable, IdentifierGenerator}
import org.hibernate.jdbc.AbstractReturningWork
import org.hibernate.service.ServiceRegistry

import java.sql.{Connection, ResultSet}
import java.util as ju

/**
 * Id generator based on function or procedure
 */
class CodeStyleGenerator extends IdentifierGenerator {
  var identifierType: Class[_] = _
  var tableName: String = _

  override def configure(ctx: GeneratorCreationContext, params: ju.Properties): Unit = {
    this.identifierType = Primitives.unwrap(ctx.getType.asInstanceOf[BasicType[_]].getJavaType)
    this.tableName = IdHelper.getTableQualifiedName(params, ctx.getServiceRegistry)
    val clz = IdHelper.getEntityClass(params, ctx.getServiceRegistry)
    require(classOf[Coded].isAssignableFrom(clz), s"CodeStyleGenerator only support Coded,${clz.getName} isn't a subclass of Coded.")
  }

  override def generate(session: SharedSessionContractImplementor, obj: Object): java.io.Serializable = {
    session.getTransactionCoordinator.createIsolationDelegate().delegateWork(
      new AbstractReturningWork[Number]() {
        def execute(conn: Connection): Number = {
          val codeId = convertCodeToId(obj.asInstanceOf[Coded].code)
          val id = if codeId <= 0 || existCodeId(codeId, conn) then getNextId(conn) else codeId
          IdHelper.convertType(id, identifierType)
        }
      }, true)
  }

  private def getNextId(connection: Connection): Long = {
    val st = connection.prepareStatement(s"select max(id) from $tableName")
    var rs: ResultSet = null
    try {
      rs = st.executeQuery()
      if rs.next() then rs.getLong(1) + 1 else 1L
    } finally {
      IOs.close(rs, st)
    }

  }

  private def existCodeId(codeId: Long, connection: Connection): Boolean = {
    val st = connection.prepareStatement(s"select code from $tableName where id=$codeId")
    var rs: ResultSet = null
    try {
      rs = st.executeQuery()
      rs.next()
    } finally {
      IOs.close(rs, st)
    }
  }

  private def convertCodeToId(code: String): Long = {
    var result = Numbers.convert2Long(code, null)
    if (null == result) {
      val builder = new StringBuilder
      for (i <- 0 until code.length) {
        val ch = code.charAt(i)
        if (Chars.isAsciiAlpha(ch)) {
          builder ++= String.valueOf(Character.toLowerCase(ch.asInstanceOf[Int]) - 'a'.asInstanceOf[Int] + 10)
        } else {
          builder ++= String.valueOf(ch)
        }
      }
      result = Numbers.convert2Long(builder.toString, 1)
    }
    if identifierType == classOf[Int] && result > Int.MaxValue then -1
    else if identifierType == classOf[Short] && result > Short.MaxValue then -1
    else result
  }
}
