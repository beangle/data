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

import scala.collection.JavaConversions._
import javax.sql.DataSource
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.dialect.H2Dialect
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.beangle.data.jdbc.dialect.PostgreSQLDialect
import org.beangle.data.jdbc.dialect.Name
import java.sql.SQLException

@RunWith(classOf[JUnitRunner])
class MetadataLoaderTest extends FlatSpec with Matchers {

  "test h2 metadata loader " should "ok" in {
    val datasource: DataSource = DataSourceUtils.build("postgresql", "postgres", "", Map("url" -> "jdbc:postgresql://localhost:5432/urp"))
    val dialect = new PostgreSQLDialect
    try {
      val schema = new Schema(dialect, null, Name("public"))
      schema.loadTables(datasource.getConnection().getMetaData(), true)
      val tables = schema.tables
      //(tables.size > 0) should be(true)
      for (table <- tables.values()) {
        val createSql = table.createSql
        (null != createSql) should be(true)
        for (fk1 <- table.foreignKeys) {
          (null != fk1.alterSql) should be(true)
        }
      }
    } catch {
      case e: SQLException => e.printStackTrace()
    }
  }
}
