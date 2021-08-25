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

import java.sql.Types

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PostgreSQLTest extends AnyFlatSpec with Matchers {

  "toType longlong varchar" should "vary from varchar to text" in {
    val t=Engines.PostgreSQL.toType(Types.VARCHAR,400000)
    val t2=Engines.PostgreSQL.toType(Types.VARCHAR,5000)
    t.name should be ("text")
    t2.name should be ("varchar(5000)")
  }
}
