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
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.{ClassLoaders, Strings}
import org.beangle.data.jdbc.meta.{Identifier, MetadataLoadSql, SqlType}

object Engine {
  val reservedWords = loadKeywords("sql-reserved.txt")

  def loadKeywords(resourceName: String): Set[String] = {
    val uri = "org/beangle/data/jdbc/engine/" + resourceName
    val lines = IOs.readLines(ClassLoaders.getResourceAsStream(uri).get)
    val keys = Collections.newSet[String]
    lines foreach { line =>
      Strings.split(line) foreach { k =>
        keys += k.toLowerCase
      }
    }
    keys.toSet
  }
}

/** RDBMS engine interface
 * It provides type mapping,default schema definition,key words,version etc.
 */
trait Engine extends Dialect {

  def name: String

  def maxIdentifierLength: Int

  def version: Version

  def defaultSchema: String

  def storeCase: StoreCase

  def keywords: Set[String]

  def quoteChars: (Char, Char)

  def toType(typeName: String): SqlType

  def toType(sqlCode: Int): SqlType

  def toType(sqlCode: Int, precision: Int): SqlType

  def toType(sqlCode: Int, precision: Int, scale: Int): SqlType

  def needQuote(name: String): Boolean = {
    val lowcaseName = name.toLowerCase
    val rs = (lowcaseName.indexOf(' ') > -1) || Engine.reservedWords.contains(lowcaseName) || keywords.contains(lowcaseName)
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
      val lowcaseName = literal.toLowerCase
      val rs = (lowcaseName.indexOf(' ') > -1) || Engine.reservedWords.contains(lowcaseName) || keywords.contains(lowcaseName)
      if (rs) {
        Identifier(literal, true)
      } else {
        storeCase match {
          case StoreCase.Lower => Identifier(literal.toLowerCase())
          case StoreCase.Upper => Identifier(literal.toUpperCase())
          case StoreCase.Mixed => Identifier(literal)
        }
      }
    }
  }

  def metadataLoadSql: MetadataLoadSql
}
