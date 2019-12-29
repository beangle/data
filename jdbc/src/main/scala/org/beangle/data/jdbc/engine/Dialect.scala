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

import org.beangle.data.jdbc.meta._


/** RDBMS Dialect
  * Focus on ddl and dml sql generation.
  */
trait Dialect {

  def createTable(table: Table): String

  def dropTable(table: String): String

  def alterTableAddColumn(table: Table, col: Column): List[String]

  def alterTableDropColumn(table: Table, col: Column): String

  def alterTableModifyColumnType(table: Table, col: Column, sqlType: SqlType): String

  def alterTableModifyColumnSetNotNull(table: Table, col: Column): String

  def alterTableModifyColumnDropNotNull(table: Table, col: Column): String

  def alterTableModifyColumnDefault(table: Table, col: Column, v: Option[String]): String

  def alterTableAddForeignKey(fk: ForeignKey): String

  def alterTableAddUnique(fk: UniqueKey): String

  def alterTableAddPrimaryKey(table: Table, pk: PrimaryKey): String

  def alterTableDropPrimaryKey(table: Table, pk: PrimaryKey): String

  def alterTableDropConstraint(table: Table, name: String): String

  def createSequence(seq: Sequence): String

  def dropSequence(seq: Sequence): String

  /** generate limit sql
    *
    * @param offset is 0 based
    */
  def limit(query: String, offset: Int, limit: Int): (String, List[Int])

  def commentsOnColumn(table: Table, column: Column, comment: Option[String]): Option[String]

  def commentsOnTable(table: Table): List[String]

  def commentsOnTable(table: String, comment: Option[String]): Option[String]

  def createIndex(i: Index): String

  def dropIndex(i: Index): String
}
