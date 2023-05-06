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

import org.beangle.data.jdbc.meta.*

/** RDBMS Dialect
 * Focus on ddl and dml sql generation.
 */
trait Dialect {

  def createSchema(name: String): String

  def createTable(table: Table): String

  def dropTable(table: String): String

  def query(table: Table): String

  def insert(table: Table): String

  def truncate(table: Table): String

  def alterTable(table: Table): AlterTableDialect

  def createSequence(seq: Sequence): String

  def dropSequence(seq: Sequence): String

  /** generate limit sql
   *
   * @param offset is 0 based
   */
  def limit(query: String, offset: Int, limit: Int): (String, List[Int])

  def commentOnColumn(table: Table, column: Column, comment: Option[String]): Option[String]

  def commentsOnTable(table: Table, includeMissing: Boolean): List[String]

  def commentOnTable(table: String, comment: Option[String]): Option[String]

  def createIndex(i: Index): String

  def dropIndex(i: Index): String

  def supportSequence: Boolean
}

trait AlterTableDialect(val table:Table) {

  def addColumn(col: Column): List[String]

  def dropColumn( col: Column): String

  def renameColumn(col: Column, newName: String): String

  def modifyColumnType(col: Column, sqlType: SqlType): String

  def modifyColumnSetNotNull(col: Column): String

  def modifyColumnDropNotNull(col: Column): String

  def modifyColumnDefault(col: Column, v: Option[String]): String

  def addForeignKey(fk: ForeignKey): String

  def addUnique(fk: UniqueKey): String

  def addPrimaryKey(pk: PrimaryKey): String

  def dropPrimaryKey(pk: PrimaryKey): String

  def dropConstraint(name: String): String

}
