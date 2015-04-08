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
package org.beangle.data.jdbc.dialect

case class Name(value: String, quoted: Boolean = false) extends Ordered[Name] {

  def toLowerCase(): Name = {
    new Name(value.toLowerCase(), quoted)
  }

  override def toString: String = {
    if (quoted) "`" + value + "`"
    else value
  }

  override def compare(other: Name): Int = {
    value.compareTo(other.value)
  }

  override def equals(other: Any): Boolean = {
    other match {
      case n: Name => n.value == this.value
      case _ => false
    }
  }
  override def hashCode: Int = {
    value.hashCode()
  }

  def attach(dialect: Dialect): Name = {
    val needQuote = dialect.needQuoted(value)
    if (needQuote != quoted) Name(value, needQuote)
    else this
  }

  def qualified(dialect: Dialect): String = {
    if (quoted) dialect.openQuote + value + dialect.closeQuote
    else value
  }
}