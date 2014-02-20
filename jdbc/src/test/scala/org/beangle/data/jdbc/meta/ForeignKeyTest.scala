/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.meta

import java.sql.Types

import org.beangle.data.jdbc.dialect.OracleDialect
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FlatSpec

class ForeignKeyTest extends FlatSpec with Matchers {

  "alter closuse " should "corret" in {
    val table = new Table("sys_table")
    val pk = new PrimaryKey("pk", new Column("id", Types.BIGINT))
    table.primaryKey = pk

    val tableA = new Table("sys_tableA")
    val fk = new ForeignKey("fkxyz", new Column("fkid", Types.BIGINT))
    tableA.addForeignKey(fk)
    fk.setReferencedTable(table)
    println(fk.getAlterSql(new OracleDialect()))
  }

}