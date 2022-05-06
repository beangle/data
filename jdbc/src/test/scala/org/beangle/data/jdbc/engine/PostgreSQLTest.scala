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

import org.beangle.data.jdbc.meta.SqlType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.sql.Types
import java.sql.Types.{BIGINT, INTEGER, SMALLINT}

class PostgreSQLTest extends AnyFlatSpec with Matchers {

  val engine = new PostgreSQL10

  "toType longlong varchar" should "vary from varchar to text" in {
    val t = engine.toType(Types.VARCHAR, 400000)
    val t2 = engine.toType(Types.VARCHAR, 5000)
    t.name should be("text")
    t2.name should be("varchar(5000)")
  }


  "big number (size >=65535) in postgresql " should " trip to less 1000 size" in {
    val scale = 0
    val precision = 65535
    engine.toType(Types.NUMERIC, precision, scale).name equals "numeric(1000, 0)" should be(true)
    engine.toType(Types.DECIMAL,  1, 0).name shouldEqual  "boolean"

    //engine.toType(Types.DECIMAL,1,0) shouldEqual SqlType(BOOLEAN, "boolean", 1)
    engine.toType(Types.DECIMAL,5,0) shouldEqual SqlType(SMALLINT, "smallint", 5)
    engine.toType(Types.DECIMAL,10,0) shouldEqual SqlType(INTEGER, "integer", 10)
    engine.toType(Types.DECIMAL,19,0) shouldEqual SqlType(BIGINT, "bigint", 19)
  }
}
