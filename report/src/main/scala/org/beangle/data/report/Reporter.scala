/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
package org.beangle.data.report

import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.Strings.isEmpty
import org.beangle.commons.lang.Strings.substringAfterLast
import org.beangle.commons.lang.Strings.substringBefore
import org.beangle.commons.lang.Strings.substringBeforeLast
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.Database
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.jdbc.util.PoolingDataSourceFactory
import org.beangle.data.report.internal.ScalaObjectWrapper
import org.beangle.data.report.model.Module
import org.beangle.data.report.model.Report
import org.umlgraph.doclet.UmlGraph
import freemarker.cache.ClassTemplateLoader
import freemarker.template.Configuration
import javax.sql.DataSource
import scala.compat.Platform

object Reporter extends Logging {

  private def checkJdkTools(): Boolean = {
    try {
      ClassLoaders.loadClass("com.sun.tools.javadoc.Main")
    } catch {
      case e: Exception => false
    }
    true
  }
  def main(args: Array[String]) {
    if (!checkJdkTools()) {
      println("Report need tools.jar which contains com.sun.tools.javadoc utility.")
      return ;
    }
    if (args.length < 1) {
      println("Usage: Reporter /path/to/your/report.xml -debug");
      return
    }
    val reportxml = new File(args(0))
    val dir = reportxml.getParent().toString() + File.pathSeparator
    logger.info("All wiki and images will be generated in {}", dir)
    val xml = scala.xml.XML.load(new FileInputStream(reportxml))
    val report = Report(xml)
    val reporter = new Reporter(report, dir)

    val tables = new collection.mutable.HashSet[Table]
    tables ++= reporter.database.tables.values

    for (module <- report.modules)
      module.filter(tables)

    for (image <- report.images)
      image.select(reporter.database.tables.values)

    val debug = if (args.length > 1) args(1) == "-debug" else false
    if (debug) {
      println("Debug Mode:Type gen to generate report again,or q or exit to quit!")
      var command = "gen"
      do {
        if (command == "gen") gen(reporter)
        print("gen/exit:")
        command = Console.in.readLine();
      } while (command != "exit" && command != "q")
    } else {
      gen(reporter)
    }
  }

  def gen(reporter: Reporter) {
    try {
      reporter.genWiki()
      reporter.genImages()
    } catch {
      case e: Exception => e.printStackTrace
    }
  }
}

class Reporter(val report: Report, val dir: String) {
  val dbconf = report.dbconf
  val ds: DataSource = new PoolingDataSourceFactory(dbconf.driver,
    dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
  val database = new Database(ds.getConnection().getMetaData(), report.dbconf.dialect, null, dbconf.schema)

  database.loadTables(true)
  database.loadSequences()

  val cfg = new Configuration()
  cfg.setTemplateLoader(new ClassTemplateLoader(getClass, "/"))
  cfg.setObjectWrapper(new ScalaObjectWrapper())

  def genWiki() {
    val data = new collection.mutable.HashMap[String, Any]()
    data += ("dialect" -> report.dbconf.dialect)
    data += ("tablesMap" -> database.tables)
    data += ("report" -> report)
    data += ("sequences" -> database.sequences)

    for (page <- report.pages) {
      if ("true" == page.iterator) {
        for (module <- report.modules)
          renderModule(module, page.name, data)
      } else {
        data.remove("module")
        render(data, page.name)
      }
    }
  }

  def renderModule(module: Module, template: String, data: collection.mutable.HashMap[String, Any]) {
    data.put("module", module)
    println("rendering module " + module + "...")

    render(data, template, module.path)
    for (module <- module.children) renderModule(module, template, data)
  }

  def genImages() {
    val data = new collection.mutable.HashMap[String, Any]()
    data += ("database" -> database)
    data += ("report" -> report)
    for (image <- report.images) {
      data.put("image", image)
      genImage(data, "class", image.name)
    }
  }

  private def render(data: Any, template: String, result: String = "") {
    val wikiResult = if (isEmpty(result)) template else result;
    val file = new File(dir + wikiResult + report.extension)
    file.getParentFile().mkdirs()
    val fw = new FileWriter(file)
    val freemarkerTemplate = cfg.getTemplate(report.template + "/" + template + ".ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
  }

  private def genImage(data: Any, template: String, result: String = "") {
    val javaResult = if (isEmpty(result)) template else result;
    val javafile = new File(dir + "images/" + javaResult + ".java")
    javafile.getParentFile().mkdirs()
    val fw = new FileWriter(javafile)
    val freemarkerTemplate = cfg.getTemplate("template/" + template + ".ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
    java2png(javafile)
    javafile.deleteOnExit()
  }

  private def java2png(file: File) {
    val javafile = file.getAbsolutePath()
    val filename = substringBefore(substringAfterLast(javafile, "/"), ".java");
    val dotfile = substringBeforeLast(javafile, "/") + "/" + filename + ".dot"
    val pngfile = substringBeforeLast(javafile, "/") + "/" + filename + ".png"
    UmlGraph.main(Array("-package", "-outputencoding", "utf-8", "-output", dotfile, javafile));
    if (new File(dotfile).exists()) {
      Runtime.getRuntime().exec("dot -Tpng -o" + pngfile + " " + dotfile);
      new File(dotfile).deleteOnExit()
    }
  }
}
