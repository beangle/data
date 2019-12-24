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
package org.beangle.data.jdbc.engine

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.{Identifier, MetadataLoadSql, SqlType}

/** RDBMS engine interface
  * It provides type mapping,default schema definition,key words,version etc.
  */
trait Engine {

  def name: String

  def version: Version

  def defaultSchema: String

  def storeCase: StoreCase.Value

  def keywords: Set[String]

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

  def metadataLoadSql: MetadataLoadSql
}