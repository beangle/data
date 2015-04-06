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
package org.beangle.data.jdbc.dialect

import java.sql.Types

import org.beangle.commons.lang.Strings

trait Dialect {

  def tableGrammar: TableGrammar

  def limitGrammar: LimitGrammar

  def sequenceGrammar: SequenceGrammar

  def defaultSchema: String

  def typeNames: TypeNames

  def translate(typeCode: Int, size: Int, scale: Int): Tuple2[Int, String]

  def keywords: Set[String]

  def supportsCascadeDelete: Boolean

  def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
    referencedTable: String, primaryKey: Iterable[String]): String

  def metadataGrammar: MetadataGrammar

  def support(version: String): Boolean

  def openQuote: Char

  def closeQuote: Char

  /**
   * Whether a store name need quoted
   */
  def needQuoted(name: String): Boolean

  /**
   * Quote a stored name to literal
   */
  def quote(name: String): String

  /**
   * Parse a literal to stored named
   */
  def parse(literal: String): Name

  /**
   * Store identifiers in which case
   */
  def storeCase: StoreCase.Value

}

object StoreCase extends Enumeration {

  val Lower, Upper, Mixed = Value

}

abstract class AbstractDialect(versions: String) extends Dialect {
  val typeNames: TypeNames = new TypeNames()
  val version: Dbversion = new Dbversion(versions)
  var keywords: Set[String] = Set.empty[String]

  this.registerType()

  override def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
    referencedTable: String, primaryKey: Iterable[String]): String = {
    val res: StringBuffer = new StringBuffer(30)
    res.append(" add constraInt ").append(constraintName).append(" foreign key (")
      .append(Strings.join(foreignKey, ", ")).append(") references ").append(referencedTable)
    if (!primaryKey.isEmpty) {
      res.append(" (").append(Strings.join(primaryKey, ", ")).append(')')
    }
    res.toString
  }

  override def supportsCascadeDelete = true

  def support(dbversion: String): Boolean = {
    if (null != version) version.contains(dbversion) else false
  }

  override def defaultSchema: String = null

  override def tableGrammar: TableGrammarBean = {
    new TableGrammarBean()
  }

  protected def registerType() {}

  protected def registerKeywords(words: List[String]): Unit = {
    for (word <- words) {
      keywords += word.toLowerCase
    }
  }

  protected def registerType(code: Int, capacity: Int, name: String): Unit = {
    typeNames.put(code, capacity, name)
  }

  protected def registerType(code: Int, name: String): Unit = {
    typeNames.put(code, name)
  }

  override def translate(typeCode: Int, size: Int, scale: Int): Tuple2[Int, String] = {
    if (typeCode == Types.OTHER) (typeCode, null) else
      try {
        (typeCode, typeNames.get(typeCode, size, size, scale))
      } catch {
        case e: Exception =>
          println("Cannot find type code" + typeCode);
          (typeCode, null)
      }
  }

  def metadataGrammar: MetadataGrammar = {
    null
  }

  def openQuote: Char = {
    '\"'
  }

  def closeQuote: Char = {
    '\"'
  }

  override def needQuoted(name: String): Boolean = {
    (name.indexOf(' ') > -1) || keywords.contains(name.toLowerCase)
  }

  override def quote(name: String): String = {
    if (needQuoted(name)) openQuote + name + closeQuote
    else name
  }

  override def parse(literal: String): Name = {
    if (Strings.isEmpty(literal)) return null
    if (literal.charAt(0) == openQuote) Name(literal.substring(1, literal.length - 1), true)
    else {
      storeCase match {
        case StoreCase.Lower => Name(literal.toLowerCase(), false)
        case StoreCase.Upper => Name(literal.toUpperCase(), false)
        case StoreCase.Mixed => Name(literal, false)
      }
    }
  }
}
