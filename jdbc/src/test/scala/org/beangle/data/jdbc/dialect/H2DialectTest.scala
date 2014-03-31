/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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

import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.jdbc.meta.Database
import org.beangle.data.jdbc.util.PoolingDataSourceFactory
import org.junit.runner.RunWith
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class H2DialectTest extends DialectTestCase {

  val properties = IOs.readJavaProperties(ClassLoaders.getResource("db.properties", getClass))

  "h2 " should "load tables and sequences" in {
    val ds: DataSource = new PoolingDataSourceFactory(properties("h2.driverClassName"),
      properties("h2.url"), properties("h2.username"), properties("h2.password"), new java.util.Properties()).getObject
    database = new Database(ds.getConnection().getMetaData(), new H2Dialect(), null, "PUBLIC")
    database.loadTables(false)
    database.loadSequences()
    listTableAndSequences

  }
}