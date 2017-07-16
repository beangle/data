/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
import org.beangle.commons.lang.Chars
import org.beangle.data.jdbc.meta.Engine

trait Dialect {

  def tableGrammar: TableGrammar

  def limitGrammar: LimitGrammar

  def sequenceGrammar: SequenceGrammar

  def defaultSchema: String

  def supportsCascadeDelete: Boolean

  def supportsCommentOn: Boolean

  def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
    referencedTable: String, primaryKey: Iterable[String]): String

  def metadataGrammar: MetadataGrammar

  def support(version: String): Boolean

  def engine: Engine

}

abstract class AbstractDialect(val engine: Engine, versions: String) extends Dialect {

  val version: Dbversion = new Dbversion(versions)

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

  override def supportsCommentOn = false

  def support(dbversion: String): Boolean = {
    if (null != version) version.contains(dbversion) else false
  }

  override def defaultSchema: String = null

  override def tableGrammar: TableGrammar = {
    new TableGrammarBean()
  }

  def metadataGrammar: MetadataGrammar = {
    null
  }

}
