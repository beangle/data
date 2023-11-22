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

package org.beangle.data.jdbc.meta

import org.beangle.data.jdbc.engine.{Engine, StoreCase}

object Identifier {
  val empty = Identifier("")
}

case class Identifier(value: String, quoted: Boolean = false) extends Ordered[Identifier] {

  assert(null != value)

  def toCase(lower: Boolean): Identifier = {
    Identifier(if (lower) value.toLowerCase() else value.toUpperCase(), quoted)
  }

  override def toString: String = {
    if (quoted) s"`$value`"
    else value
  }

  override def compare(other: Identifier): Int = {
    value.compareTo(other.value)
  }

  override def equals(other: Any): Boolean = {
    other match {
      case n: Identifier => n.value == this.value
      case _ => false
    }
  }

  override def hashCode: Int = {
    value.hashCode()
  }

  def attach(engine: Engine): Identifier = {
    val v = engine.storeCase match
      case StoreCase.Mixed => value
      case StoreCase.Lower => if mixedCase then value else value.toLowerCase
      case StoreCase.Upper => if mixedCase then value else value.toUpperCase

    val needQuote = engine.needQuote(v)
    if needQuote != quoted || v != value then Identifier(v, needQuote) else this
  }

  private def mixedCase: Boolean = {
    var hasLower = false
    var hasUpper = false
    value.toCharArray foreach { c =>
      if Character.isLowerCase(c) then hasLower = true
      else if Character.isUpperCase(c) then hasUpper = true
    }
    hasLower && hasUpper
  }

  def toLiteral(engine: Engine): String = {
    if (quoted) {
      val qc = engine.quoteChars
      s"${qc._1}$value${qc._2}"
    } else {
      value
    }
  }
}
