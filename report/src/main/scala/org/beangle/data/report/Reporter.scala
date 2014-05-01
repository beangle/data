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
package org.beangle.data.report

import java.io.{ File, FileInputStream }
import java.util.Locale
import org.beangle.commons.io.Files.{ /, forName, stringWriter }
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.Strings.{ isEmpty, substringAfterLast, substringBefore, substringBeforeLast }
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.{ Database, Table }
import org.beangle.data.jdbc.util.PoolingDataSourceFactory
import org.beangle.data.report.internal.ScalaObjectWrapper
import org.beangle.data.report.model.{ Module, Report }
import freemarker.cache.{ ClassTemplateLoader, FileTemplateLoader, MultiTemplateLoader }
import freemarker.template.Configuration
import javax.sql.DataSource
import org.umlgraph.doclet.UmlGraph

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
      info("Report need tools.jar which contains com.sun.tools.javadoc utility.")
      return ;
    }
    if (args.length < 1) {
      info("Usage: Reporter /path/to/your/report.xml -debug");
      return
    }

    val reportxml = new File(args(0))
    var dir = reportxml.getAbsolutePath()
    dir = substringBeforeLast(dir, /) + / + substringBefore(substringAfterLast(dir, /), ".xml") + /
    info(s"All wiki and images will be generated in $dir")
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
      info("Debug Mode:Type gen to generate report again,or q or exit to quit!")
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
      info("report generate complete.")
    } catch {
      case e: Exception => e.printStackTrace
    }
  }
}

class Reporter(val report: Report, val dir: String) extends Logging {
  val dbconf = report.dbconf
  val ds: DataSource = new PoolingDataSourceFactory(dbconf.driver,
    dbconf.url, dbconf.user, dbconf.password, dbconf.props).getObject
  val database = new Database(ds.getConnection().getMetaData(), report.dbconf.dialect, null, dbconf.schema)

  database.loadTables(true)
  database.loadSequences()

  val cfg = new Configuration()
  cfg.setEncoding(Locale.getDefault, "UTF-8")
  val overrideDir = new File(dir + ".." + / + "template")
  if (overrideDir.exists) {
    info(s"Load override template from ${overrideDir.getAbsolutePath()}")
    cfg.setTemplateLoader(new MultiTemplateLoader(Array(new FileTemplateLoader(overrideDir), new ClassTemplateLoader(getClass, "/template"))))
  } else
    cfg.setTemplateLoader(new ClassTemplateLoader(getClass, "/template"))
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
    info(s"rendering module $module...")

    render(data, template, module.path)
    for (module <- module.children) renderModule(module, template, data)
  }

  def genImages() {
    val data = new collection.mutable.HashMap[String, Any]()
    data += ("database" -> database)
    data += ("report" -> report)
    for (image <- report.images) {
      data.put("image", image)
      genImage(data, image.name)
    }
  }

  private def render(data: Any, template: String, result: String = "") {
    val wikiResult = if (isEmpty(result)) template else result;
    val file = new File(dir + wikiResult + report.extension)
    file.getParentFile().mkdirs()
    val fw = stringWriter(file)
    val freemarkerTemplate = cfg.getTemplate(report.template + "/" + template + ".ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
  }

  private def genImage(data: Any, result: String) {
    val javafile = new File(dir + "images" + / + result + ".java")
    javafile.getParentFile().mkdirs()
    val fw = stringWriter(javafile)
    val freemarkerTemplate = cfg.getTemplate("class.ftl")
    freemarkerTemplate.process(data, fw)
    fw.close()
    java2png(javafile)
    javafile.deleteOnExit()
  }

  private def java2png(javafile: File) {
    val javaPath = javafile.getAbsolutePath()
    val filename = substringBefore(substringAfterLast(javaPath, /), ".java")
    val dotPath = substringBeforeLast(javaPath, /) + / + filename + ".dot"
    val pngPath = substringBeforeLast(javaPath, /) + / + filename + ".png"
    val dotfile = forName(dotPath)
    UmlGraph.main(Array("-package", "-outputencoding", "utf-8", "-output", dotPath, javaPath));
    if (dotfile.exists) {
      Runtime.getRuntime().exec("dot -Tpng -o" + pngPath + " " + dotPath);
      dotfile.deleteOnExit()
    }
  }
}
