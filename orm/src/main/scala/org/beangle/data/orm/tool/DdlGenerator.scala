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
package org.beangle.data.orm.tool

import java.io.FileWriter
import java.util.Locale

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.data.jdbc.meta.{ Database, Engines }
import org.beangle.commons.lang.{ Locales, SystemInfo }
import org.beangle.data.orm.Mappings
import org.beangle.data.orm.SchemaExporter
import org.beangle.data.jdbc.dialect.Dialects
import org.beangle.commons.lang.Strings

/**
 * Generate DDL and Sequences and Comments
 */
object DdlGenerator {
  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      System.out.println("Usage: DdlGenerator PostgreSQL /tmp zh_CN com.my.package")
      return
    }
    var dir = SystemInfo.tmpDir
    if (args.length > 1) dir = args(1)
    var locale = Locale.getDefault
    if (args.length > 2) locale = Locales.toLocale(args(2))
    var pattern: String = null
    if (args.length > 3) pattern = args(3)

    var dialectName = args(0)

    val dialect = Dialects.forName(dialectName)
    val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle/orm.xml")
    println(ormLocations)
    val mappings = new Mappings(new Database(dialect.engine), ormLocations)
    mappings.autobind()
    val scripts = new SchemaExporter(mappings, dialect, locale, pattern).generate()

    //export to files
    writeTo(dir, "0-schemas.sql", scripts.schemas)
    writeTo(dir, "1-tables.sql", scripts.tables)
    writeTo(dir, "2-constraints.sql", scripts.constraints)
    writeTo(dir, "3-indices.sql", scripts.indices)
    writeTo(dir, "4-sequences.sql", scripts.sequences)
    writeTo(dir, "5-comments.sql", scripts.comments)
  }

  private def writeTo(dir: String, file: String, contents: List[String]): Unit = {
    if (null != contents && !contents.isEmpty) {
      val writer = new FileWriter(dir + "/" + file, false)
      contents foreach { c =>
        writer.write(c)
        writer.write(";\n")
      }
      writer.flush
      writer.close
    }
  }
}
