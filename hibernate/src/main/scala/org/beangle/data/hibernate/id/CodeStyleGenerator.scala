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

import java.{ util => ju }

import org.beangle.commons.lang.{ Chars, Numbers }
import org.beangle.data.model.pojo.Coded
import org.hibernate.`type`.{ IntegerType, LongType, ShortType, Type }
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.id.{ Configurable, IdentifierGenerator }
import org.hibernate.service.ServiceRegistry
import org.hibernate.engine.spi.SharedSessionContractImplementor

/**
 * Id generator based on function or procedure
 */
class CodeStyleGenerator extends IdentifierGenerator {
  var identifierType: Type = _

  override def configure(t: Type, params: ju.Properties, sr: ServiceRegistry): Unit = {
    this.identifierType = t;
  }

  override def generate(session: SharedSessionContractImplementor, obj: Object): java.io.Serializable = {
    obj match {
      case coded: Coded =>
        var result = identifierType match {
          case lt: LongType    => Numbers.convert2Long(coded.code, null)
          case it: IntegerType => Numbers.convert2Int(coded.code, null)
          case st: ShortType   => Numbers.convert2Short(coded.code, null)
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
            case lt: LongType    => Numbers.convert2Long(builder.toString)
            case it: IntegerType => Numbers.convert2Int(builder.toString)
            case st: ShortType   => Numbers.convert2Short(builder.toString)
          }
        }
        result
      case _ => throw new RuntimeException("CodedIdGenerator only support Coded")
    }
  }
}
