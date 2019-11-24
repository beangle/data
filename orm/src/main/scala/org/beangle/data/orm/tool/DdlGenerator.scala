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
package org.beangle.data.orm.tool

import java.io.FileWriter
import java.util.Locale

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.{Locales, SystemInfo}
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.dialect.{Dialect, Dialects, SQL}
import org.beangle.data.jdbc.meta.{DBScripts, Database, Table}
import org.beangle.data.orm.Mappings

/**
  * Generate DDL and Sequences and Comments
  */
object DdlGenerator {
  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      System.out.println("Usage: DdlGenerator PostgreSQL /tmp zh_CN")
      return
    }
    var dir = SystemInfo.tmpDir
    if (args.length > 1) dir = args(1)
    var locale = Locale.getDefault
    if (args.length > 2) locale = Locales.toLocale(args(2))
    val dialectName = args(0)

    val dialect = Dialects.forName(dialectName)
    val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle/orm.xml")
    val mappings = new Mappings(new Database(dialect.engine), ormLocations)
    mappings.locale = locale
    mappings.autobind()
    val scripts = new SchemaExporter(mappings, dialect).generate()

    //export to files
    writeTo(dir, "0-schemas.sql", scripts.schemas)
    writeTo(dir, "1-tables.sql", scripts.tables)
    writeTo(dir, "2-constraints.sql", scripts.constraints)
    writeTo(dir, "3-indices.sql", scripts.indices)
    writeTo(dir, "4-sequences.sql", scripts.sequences)
    writeTo(dir, "5-comments.sql", scripts.comments)
  }

  private def writeTo(dir: String, file: String, contents: List[String]): Unit = {
    if (null != contents && contents.nonEmpty) {
      val writer = new FileWriter(dir + "/" + file, false)
      contents foreach { c =>
        writer.write(c)
        writer.write(";\n")
      }
      writer.flush()
      writer.close()
    }
  }
}

class SchemaExporter(mappings: Mappings, dialect: Dialect) extends Logging {
  private val schemas = new collection.mutable.ListBuffer[String]
  private val tables = new collection.mutable.ListBuffer[String]
  private val sequences = new collection.mutable.ListBuffer[String]
  private val comments = new collection.mutable.ListBuffer[String]
  private val constraints = new collection.mutable.ListBuffer[String]
  private val indexes = new collection.mutable.ListBuffer[String]
  private val processed = new collection.mutable.HashSet[Table]

  def generate(): DBScripts = {
    val database = mappings.database
    database.schemas.values foreach {
      schema => schema.tables.values foreach { t => if (!t.phantom) generateTableSql(t) }
    }
    val scripts = new DBScripts()
    schemas ++= database.schemas.keys.filter(i => i.value.length > 0).map(s => s"create schema $s")
    scripts.schemas = schemas.sorted.toList
    scripts.comments = comments.toSet.toList.sorted
    scripts.tables = tables.sorted.toList
    scripts.sequences = sequences.sorted.toList
    scripts.constraints = constraints.sorted.toList
    scripts
  }

  private def generateTableSql(table: Table): Unit = {
    if (processed.contains(table)) return
    processed.add(table)
    comments ++= SQL.commentsOnTable(table, dialect)
    tables += SQL.createTable(table, dialect)

    table.foreignKeys foreach { fk =>
      constraints += SQL.alterTableAddforeignKey(fk, dialect)
    }
    table.uniqueKeys foreach { uk =>
      constraints += SQL.alterTableAddUnique(uk, dialect)
    }

    table.indexes foreach { idx =>
      indexes += SQL.createIndex(idx)
    }
  }

}
