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

import org.scalatest.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class SQLServerTest extends AnyFlatSpec with Matchers {
  var engine = Engines.SQLServer
  val rs1 = engine.limit("select * from users", 0, 10)
  val rs2 = engine.limit("select * from users order by name", 0, 10)
  val rs3 = engine.limit("select * FROM users order by name", 10, 10)

  println(rs1)
  println(rs2)
  println(rs3)
}
