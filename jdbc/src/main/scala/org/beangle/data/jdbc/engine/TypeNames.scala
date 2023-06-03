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

package org.beangle.data.jdbc.engine

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.{Numbers, Strings}
import org.beangle.data.jdbc.meta.*

import java.sql.Types
import java.sql.Types.*
import scala.collection.mutable

object TypeNames {

  /** TypeInfo
   *
   * @param name
   * @param category varchar/number
   * @param precision
   * @param scale
   */
  case class TypeInfo(name: String, category: String, precision: Option[String], scale: Option[String]) {
    def precisionValue: Int = {
      precision match {
        case Some(p) => if (Numbers.isDigits(p)) Numbers.toInt(p) else throw new RuntimeException("Cannot parse precision $p")
        case None => 0
      }
    }

    def scaleValue: Int = {
      scale match {
        case Some(p) => if (Numbers.isDigits(p)) Numbers.toInt(p) else throw new RuntimeException("Cannot parse scale $p")
        case None => 0
      }
    }

  }

  class Builder {
    private val code2names = Collections.newMap[Int, mutable.Buffer[(Int, String)]]
    private val name2codes = Collections.newMap[String, mutable.Buffer[(Int, Int)]]

    def put(typecode: Int, value: String): Unit = {
      put(typecode, 0, value)
    }

    /** 注册类型代码和模式
     *
     * @param typecode 类型代码
     * @param capacity 该类型的length或者precision
     * @param pattern  类型的模式
     */
    def put(typecode: Int, capacity: Int, pattern: String): Unit = {
      val info = parse(pattern)

      val names = code2names.getOrElseUpdate(typecode, new mutable.ArrayBuffer[(Int, String)])
      names += (capacity -> info.name)

      val codes = name2codes.getOrElseUpdate(info.category, new mutable.ArrayBuffer[(Int, Int)])
      val precision = info.precision match {
        case Some(p) =>
          if (capacity == 0) {
            if (Numbers.isDigits(p)) Numbers.toInt(p) else 0
          } else {
            capacity
          }
        case None => capacity
      }
      codes += (precision -> typecode)
    }

    def build(): TypeNames = {
      val names = code2names.map { case (code, names) =>
        (code, names.sortBy(_._1).toList)
      }
      val codes = name2codes.map { case (name, codes) =>
        (name, codes.sortBy(_._1).toList)
      }
      new TypeNames(names.toMap, codes.toMap)
    }
  }

  protected[TypeNames] def parse(pattern: String): TypeInfo = {
    var precision = ""
    var scale = ""
    val category = if (pattern.contains("(")) {
      if (pattern.contains(",")) {
        precision = Strings.substringBetween(pattern, "(", ",")
        if (pattern.contains(")")) {
          scale = Strings.substringBetween(pattern, ",", ")")
        } else {
          throw new RuntimeException(s"Unrecognized sql type $pattern")
        }
      } else if (pattern.contains(")")) {
        precision = Strings.substringBetween(pattern, "(", ")")
      } else {
        throw new RuntimeException(s"Unrecognized sql type $pattern")
      }
      Strings.substringBefore(pattern, "(").toLowerCase
    } else {
      pattern.toLowerCase()
    }
    TypeInfo(pattern.toLowerCase, category, if (precision.isBlank) None else Some(precision), if (scale.isBlank) None else Some(scale))
  }
}

class TypeNames(private val code2names: Map[Int, List[(Int, String)]],
                private val name2codes: Map[String, List[(Int, Int)]]) {

  /** get default type name for specified type
   *
   * @param typecode the type key
   * @return the default type name associated with specified key
   */
  def toType(typecode: Int): SqlType = {
    toType(typecode, 0, 0)
  }

  def toType(sqlCode: Int, precision: Int, scale: Int): SqlType = {
    val targetCode = normalize(sqlCode, precision)
    SqlType(targetCode, toName(targetCode, precision, scale), precision, scale)
  }

  def toType(typeName: String): SqlType = {
    val info = TypeNames.parse(typeName)
    val precision = info.precisionValue
    val code = toCode(info.category, precision)
    SqlType(code, typeName, precision, info.scaleValue)
  }

  protected[engine] def toName(typecode: Int): String = {
    code2names.get(normalize(typecode, 0)) match {
      case None => "other"
      case Some(l) => l.head._2
    }
  }

  /**
   * get type name for specified type and size
   *
   * @param typecode  the type key
   * @param precision the SQL precision
   * @param scale     the SQL scale
   * @return the associated name with smallest capacity >= size, if available
   *         and the default type name otherwise
   */
  protected[engine] def toName(typecode: Int, precision: Int, scale: Int): String = {
    code2names.get(typecode) match {
      case None => "other"
      case Some(l) =>
        val name = l.find(precision <= _._1) match {
          case None => replace(l.head._2, precision, scale)
          case Some(n) => replace(n._2, precision, scale)
        }
        if name.contains("(0)") then Strings.replace(name, "(0)", "")
        else if name.contains("(0,0)") then Strings.replace(name, "(0,0)", "")
        else name
    }
  }

  protected[engine] def toCode(typeName: String, precision: Int): Int = {
    name2codes.get(typeName) match {
      case None => Types.OTHER
      case Some(l) =>
        l.find(precision <= _._1) match {
          case None => l.head._2
          case Some(n) => n._2
        }
    }
  }

  private def normalize(sqlCode: Int, precision: Int): Int = {
    sqlCode match {
      case DECIMAL | NUMERIC =>
        precision match {
          case 1 => BOOLEAN
          case 5 => SMALLINT
          case 10 => INTEGER
          case 19 => BIGINT
          case _ => sqlCode
        }
      case _ => sqlCode
    }
  }

  private def replace(typeString: String, precision: Int, scale: Int) = {
    var finalType = typeString
    finalType = Strings.replace(finalType, "$s", scale.toString)
    finalType = Strings.replace(finalType, "$l", precision.toString)
    Strings.replace(finalType, "$p", precision.toString)
  }

}
