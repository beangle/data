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
package org.beangle.data.jdbc.internal

import scala.xml.Node

object NodeOps {

  import scala.language.implicitConversions
  @inline implicit def node2Ops(n: Node): NodeOps = {
    new NodeOps(n)
  }
}

final class NodeOps(val n: Node) extends AnyVal {
  @inline
  def attr(name: String): String = {
    (n \ s"@$name").text
  }

  @inline
  def name: String = {
    (n \ s"@name").text
  }

  @inline
  def get(name: String): Option[String] = {
    (n \ s"@$name").map(_.text).headOption
  }

}
