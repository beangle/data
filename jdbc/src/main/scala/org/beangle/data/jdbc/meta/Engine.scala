/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.meta

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta

trait Engine {

  def storeCase: StoreCase.Value

  def keywords: Set[String]

  def typeNames: TypeNames

  def quoteChars: (Char, Char)

  def toType(typeName: String): SqlType

  def toType(sqlCode: Int): SqlType

  def toType(sqlCode: Int, precision: Int): SqlType

  def toType(sqlCode: Int, precision: Int, scale: Int): SqlType

  def needQuote(name: String): Boolean = {
    val rs = (name.indexOf(' ') > -1) || keywords.contains(name.toLowerCase)
    if (rs) return true
    storeCase match {
      case StoreCase.Lower => name.exists { c => Character.isUpperCase(c) }
      case StoreCase.Upper => name.exists { c => Character.isLowerCase(c) }
      case StoreCase.Mixed => false
    }
  }

  def quote(name: String): String = {
    if (needQuote(name)) {
      val qc = quoteChars
      s"${qc._1}$name${qc._2}"
    } else {
      name
    }
  }

  def toIdentifier(literal: String): Identifier = {
    if (Strings.isEmpty(literal)) return Identifier.empty
    if (literal.charAt(0) == quoteChars._1) {
      val content = literal.substring(1, literal.length - 1)
      storeCase match {
        case StoreCase.Lower => Identifier(content, content == content.toLowerCase())
        case StoreCase.Upper => Identifier(content, content == content.toUpperCase())
        case StoreCase.Mixed => Identifier(content)
      }
    } else {
      storeCase match {
        case StoreCase.Lower => Identifier(literal.toLowerCase())
        case StoreCase.Upper => Identifier(literal.toUpperCase())
        case StoreCase.Mixed => Identifier(literal)
      }
    }
  }

}

abstract class AbstractEngine extends Engine {
  var typeNames: TypeNames = _

  private var typeMappingBuilder = new meta.TypeNames.Builder()

  var keywords: Set[String] = Set.empty[String]

  def registerKeywords(words: String*): Unit = {
    keywords ++= words.toList
  }

  override def quoteChars: (Char, Char) = {
    ('\"', '\"')
  }

  protected def registerTypes(tuples: (Int, String)*): Unit = {
    tuples foreach { tuple =>
      typeMappingBuilder.put(tuple._1, tuple._2)
    }
    typeNames = typeMappingBuilder.build()
  }

  /** 按照该类型的容量进行登记
    *
    * @param tuples
    */
  protected def registerTypes2(tuples: (Int, Int, String)*): Unit = {
    tuples foreach { tuple =>
      typeMappingBuilder.put(tuple._1, tuple._2, tuple._3)
    }
    typeNames = typeMappingBuilder.build()
  }

  def toType(typeName: String): SqlType = {
    typeNames.toType(typeName)
  }

  override final def toType(sqlCode: Int): SqlType = {
    toType(sqlCode, 0, 0)
  }

  override final def toType(sqlCode: Int, precision: Int): SqlType = {
    toType(sqlCode, precision, 0)
  }

  override def toType(sqlCode: Int, precision: Int, scale: Int): SqlType = {
    typeNames.toType(sqlCode, precision, scale)
  }

  def storeCase: StoreCase.Value = {
    StoreCase.Mixed
  }
}
