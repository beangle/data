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

import java.io.{File, FileWriter}
import java.util.Locale

import org.beangle.commons.collection.Collections
import org.beangle.commons.io.Files./
import org.beangle.commons.io.{Dirs, IOs, ResourcePatternResolver}
import org.beangle.commons.lang.{Locales, Strings, SystemInfo}
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.engine.{Engine, Engines}
import org.beangle.data.jdbc.meta.{DBScripts, Database, Identifier, Serializer, Table}
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
    val warnings = new collection.mutable.ListBuffer[String]
    Strings.split(dialectName) foreach { d =>
      warnings ++= gen(d, dir + / + d.toLowerCase, locale)
    }
    val w = warnings.toSet.toBuffer.sorted
    writeTo(dir, "warnings.txt", w)
  }

  private def gen(dialect: String, dir: String, locale: Locale): List[String] = {
    val target= new File(dir)
    target.mkdirs()
    if(!target.exists()){
      println("Cannot makdir "+ target.getAbsolutePath)
      return List.empty
    }
    val engine = Engines.forName(dialect)
    val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle/orm.xml")
    val database = new Database(engine)
    val mappings = new Mappings(database, ormLocations)
    mappings.locale = locale
    mappings.autobind()
    val scripts = new SchemaExporter(mappings, engine).generate()

    //export to files
    writeTo(dir, "0-schemas.sql", scripts.schemas)
    writeTo(dir, "1-tables.sql", scripts.tables)
    writeTo(dir, "2-keys.sql", scripts.keys)
    writeTo(dir, "3-indices.sql", scripts.indices)
    writeTo(dir, "4-constraints.sql", scripts.constraints)
    writeTo(dir, "5-sequences.sql", scripts.sequences)
    writeTo(dir, "6-comments.sql", scripts.comments)
    writeLinesTo(dir, "7-auxiliaries.sql", scripts.auxiliaries)
    writeLinesTo(dir, "database.xml", List(Serializer.toXml(database)))
    scripts.warnings
  }

  private def writeLinesTo(dir: String, file: String, contents: List[String]): Unit = {
    if (contents.nonEmpty) {
      val writer = new FileWriter(dir + "/" + file, false)
      contents foreach { c =>
        if (null != c && c.nonEmpty) {
          writer.write(c)
        }
      }
      writer.flush()
      writer.close()
    }
  }

  private def writeTo(dir: String, file: String, contents: collection.Seq[String]): Unit = {
    if (null != contents && contents.nonEmpty) {
      val writer = new FileWriter(dir + "/" + file, false)
      contents foreach { c =>
        writer.write(c)
        writer.write(";\n")
      }
      writer.flush()
      writer.close()
    } else {
      new File(dir + "/" + file).delete()
    }
  }
}

class SchemaExporter(mappings: Mappings, engine: Engine) extends Logging {
  private val schemas = new collection.mutable.ListBuffer[String]
  private val tables = new collection.mutable.ListBuffer[String]
  private val sequences = new collection.mutable.ListBuffer[String]
  private val keys = new collection.mutable.ListBuffer[String]
  private val comments = new collection.mutable.ListBuffer[String]
  private val constraints = new collection.mutable.ListBuffer[String]
  private val indexes = new collection.mutable.ListBuffer[String]
  private val warnings = new collection.mutable.ListBuffer[String]
  private val processed = new collection.mutable.HashSet[Table]

  def generate(): DBScripts = {
    val database = mappings.database
    database.schemas.values foreach {
      schema => schema.tables.values foreach generateTableSql
    }
    val scripts = new DBScripts()
    val uncommentLines = comments.count(_.contains("?"))
    if (uncommentLines > 0) {
      warnings += s"${engine.name}:find ${uncommentLines} uncomment lines"
    }
    if (database.hasQuotedIdentifier) {
      warnings += s"${engine.name}:find quoted identifiers"
    }
    schemas ++= database.schemas.keys.filter(i => i.value.length > 0).map(s => s"create schema $s")
    scripts.schemas = schemas.sorted.toList
    scripts.comments = comments.toSet.toList.sorted
    scripts.tables = tables.sorted.toList
    scripts.keys = keys.sorted.toList
    scripts.indices = indexes.sorted.map(x => Strings.substringAfter(x, "--")).toList
    scripts.sequences = sequences.sorted.toList
    scripts.constraints = constraints.sorted.toList
    val auxiliaries = Collections.newBuffer[String]
    val dialectShortName = engine.getClass.getSimpleName.toLowerCase
    ResourcePatternResolver.getResources(s"classpath*:META-INF/beangle/ddl/$dialectShortName/*.sql") foreach { r =>
      auxiliaries += IOs.readString(r.openStream())
    }
    scripts.auxiliaries = auxiliaries.toList
    scripts.warnings = warnings.toList
    scripts
  }

  private def generateTableSql(table: Table): Unit = {
    if (processed.contains(table)) return
    processed.add(table)
    checkNameLength(table.schema.name.value, table.name)
    comments ++= engine.commentsOnTable(table,true)
    tables += engine.createTable(table)

    table.primaryKey foreach { pk =>
      checkNameLength(table.qualifiedName, pk.name)
      keys += engine.alterTableAddPrimaryKey(table, pk)
    }

    table.uniqueKeys foreach { uk =>
      checkNameLength(table.qualifiedName, uk.name)
      keys += engine.alterTableAddUnique(uk)
    }

    table.indexes foreach { idx =>
      checkNameLength(idx.literalName, idx.name)
      //for order by table
      indexes += table.qualifiedName + "--" + engine.createIndex(idx)
    }

    table.foreignKeys foreach { fk =>
      constraints += engine.alterTableAddForeignKey(fk)
    }
  }

  def checkNameLength(owner: String, i: Identifier): Unit = {
    if (i.value.length > engine.maxIdentifierLength) {
      warnings += s"${engine.name}:${owner}.${i.value}'s length is ${i.value.length},greate than ${engine.maxIdentifierLength}"
    }
  }
}
