/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.junit.runner.RunWith
import javax.sql.DataSource
import org.scalatest.junit.JUnitRunner
import org.beangle.data.jdbc.meta.Engines
import org.beangle.data.jdbc.meta.Database
import org.beangle.data.jdbc.meta.Schema
import org.beangle.data.jdbc.meta.Identifier
import org.beangle.data.jdbc.meta.MetadataLoader

@RunWith(classOf[JUnitRunner])
class H2DialectTest extends DialectTestCase {

  val properties = ClassLoaders.getResource("db.properties") match {
    case Some(r) => IOs.readJavaProperties(r)
    case None    => Map.empty[String, String]
  }

  println(ClassLoaders.getResource("db.properties"))
  "h2 " should "load tables and sequences" in {
    val ds: DataSource = DataSourceUtils.build("h2", properties("h2.username"), properties("h2.password"), Map("url" -> properties("h2.url")))

    val meta = ds.getConnection().getMetaData()
    val database = new Database(Engines.H2)
    schema = database.getOrCreateSchema(Identifier("PUBLIC"))
    val loader = new MetadataLoader(schema, new H2Dialect, meta)
    loader.loadTables(false)
    loader.loadSequences()
    listTableAndSequences
  }
}
