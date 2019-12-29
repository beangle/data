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

import javax.sql.DataSource
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.ds.DataSourceUtils
import org.beangle.data.jdbc.meta.{Database, Identifier, MetadataLoader, Schema}
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class H2Test extends AnyFlatSpec with Matchers with Logging {
  protected var schema: Schema = _

  protected def listTableAndSequences = {
    val tables = schema.tables
    for (name <- tables.keySet) {
      logger.info(s"table $name")
    }

    val seqs = schema.sequences
    for (obj <- seqs) {
      logger.info(s"sequence $obj")
    }
  }

  val properties = ClassLoaders.getResource("db.properties") match {
    case Some(r) => IOs.readJavaProperties(r)
    case None => Map.empty[String, String]
  }

  println(ClassLoaders.getResource("db.properties"))
  "h2 " should "load tables and sequences" in {
    val ds: DataSource = DataSourceUtils.build("h2", properties("h2.username"), properties("h2.password"), Map("url" -> properties("h2.url")))

    val meta = ds.getConnection().getMetaData()
    val database = new Database(Engines.H2)
    schema = database.getOrCreateSchema(Identifier("PUBLIC"))
    val loader = new MetadataLoader(meta, Engines.H2)
    loader.loadTables(schema, false)
    loader.loadSequences(schema)
    listTableAndSequences
  }
}
