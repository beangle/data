/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.jdbc.meta

import org.beangle.data.jdbc.dialect._
import org.beangle.commons.lang.Strings

class Sequence(var name: Name, var schema: Name) extends Ordered[Sequence] {

  var dialect: Dialect = _

  var current: Long = 0

  var increment: Int = 1

  var cache: Int = 32

  var cycle: Boolean = _

  def qualifiedName: String = {
    if (null == schema) name.qualified(dialect)
    else schema.qualified(dialect) + "." + name.qualified(dialect)
  }

  def toCase(lower: Boolean): Unit = {
    this.schema = schema.toCase(lower)
    this.name = name.toCase(lower)
  }

  def attach(dialect: Dialect): this.type = {
    this.name = name.attach(dialect)
    this.schema = schema.attach(dialect)
    this.dialect = dialect
    this
  }

  def createSql: String = {
    if (null == dialect.sequenceGrammar) return null
    var sql: String = dialect.sequenceGrammar.createSql
    sql = sql.replace(":name", qualifiedName)
    sql = sql.replace(":start", String.valueOf(current + 1))
    sql = sql.replace(":increment", String.valueOf(increment))
    sql = sql.replace(":cache", String.valueOf(cache))
    sql = sql.replace(":cycle", if (cycle) "cycle" else "")
    return sql
  }

  def dropSql: String = {
    if (null == dialect.sequenceGrammar) return null
    var sql: String = dialect.sequenceGrammar.dropSql;
    sql = sql.replace(":name", qualifiedName)
    return sql
  }

  override def toString = name.toString

  override def compare(o: Sequence): Int = {
    name.compareTo(o.name)
  }

  override def hashCode = name.hashCode()

  /**
   * 比较name
   */
  override def equals(rhs: Any) = name.value.equals(rhs.asInstanceOf[Sequence].name.value)
}
