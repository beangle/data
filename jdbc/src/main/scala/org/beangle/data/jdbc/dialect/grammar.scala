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
  var tableComment: String = _
  var columnComent: String = _
  var supportsUnique: Boolean = true
  var supportsNullUnique: Boolean = true
  var supportsColumnCheck: Boolean = true

  var dropSql: String = "drop table {}"

  var createString: String = "create table"

  def getComment(comment: String): String =
    if (null == this.tableComment) "" else Strings.replace(this.tableComment, "{}", comment)

  def getColumnComment(comment: String): String = {
    if (null == this.columnComent) ""
    else {
      var newcomment = Strings.replace(comment, "'", "")
      newcomment = Strings.replace(newcomment, "\"", "")
      Strings.replace(this.columnComent, "{}", newcomment)
    }
  }

  def dropCascade(table: String): String = Strings.replace(dropSql, "{}", table)
}

trait LimitGrammar {

  /**
    * @param offset is 0 based
    */
  def limit(query: String, offset: Int, limit: Int): (String, List[Int])

}

class LimitGrammarBean(pattern: String, offsetPattern: String, val bindInReverseOrder: Boolean) extends LimitGrammar {

  def limit(query: String, offset: Int, limit: Int): (String, List[Int]) = {
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

class SequenceGrammar {
  var createSql: String = "create sequence :name start with :start increment by :increment :cycle"
  var dropSql: String = "drop sequence :name"
  var nextValSql: String = _
  var selectNextValSql: String = _
  var querySequenceSql: String = _
}

class MetadataGrammar(val primaryKeysql: String, val importedKeySql: String, val indexInfoSql: String) {

}
