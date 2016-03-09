/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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

import org.beangle.commons.lang.Strings

trait TableGrammar {

  def nullColumnString: String

  def createString: String

  def getComment(comment: String): String

  def dropCascade(table: String): String

  def getColumnComment(comment: String): String

  def supportsUnique: Boolean

  def supportsNullUnique: Boolean

  def supportsColumnCheck: Boolean
}

class TableGrammarBean extends TableGrammar {

  var nullColumnString: String = ""
  var tableComment: String = null
  var columnComent: String = null
  var supportsUnique: Boolean = true
  var supportsNullUnique: Boolean = true
  var supportsColumnCheck: Boolean = true

  var dropSql: String = "drop table {}"

  var createString: String = "create table"

  def getComment(comment: String) =
    if (null == this.tableComment) "" else Strings.replace(this.tableComment, "{}", comment)

  def getColumnComment(comment: String) = {
    if (null == this.columnComent) ""
    else {
      var newcomment = Strings.replace(comment, "'", "")
      newcomment = Strings.replace(newcomment, "\"", "")
      Strings.replace(this.columnComent, "{}", newcomment)
    }
  }

  def dropCascade(table: String) = Strings.replace(dropSql, "{}", table)

}

trait LimitGrammar {

  /**
   * @param offset is 0 based 
   */
  def limit(query: String, offset: Int, limit: Int): Tuple2[String, List[Int]]

}

class LimitGrammarBean(pattern: String, offsetPattern: String, val bindInReverseOrder: Boolean) extends LimitGrammar {

  def limit(query: String, offset: Int, limit: Int): Tuple2[String, List[Int]] = {
    val hasOffset = offset > 0
    val limitOrMax = if (null == offsetPattern) offset + limit else limit

    if (hasOffset) {
      val params = if (bindInReverseOrder) List(limitOrMax, offset) else List(offset, limitOrMax)
      (Strings.replace(offsetPattern, "{}", query), params)
    } else {
      (Strings.replace(pattern, "{}", query), List(limitOrMax))
    }
  }
}

/**
 * sequence grammar
 * @author chaostone
 *
 */
class SequenceGrammar {

  var createSql: String = "create sequence :name start with :start increment by :increment :cycle"
  var dropSql: String = "drop sequence :name"
  var nextValSql: String = null
  var selectNextValSql: String = null
  var querySequenceSql: String = null

}

class MetadataGrammar(val primaryKeysql: String, val importedKeySql: String, val indexInfoSql: String) {

}
