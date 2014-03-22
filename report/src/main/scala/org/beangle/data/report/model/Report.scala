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
package org.beangle.data.report.model

import org.beangle.data.jdbc.util.DbConfig
import org.beangle.commons.lang.Strings
import org.beangle.commons.bean.Initializing

object Report {

  def apply(xml: scala.xml.Elem): Report = {
    val report = new Report(DbConfig.build(xml))
    report.title = (xml \ "@title").text
    report.system.name = (xml \ "system" \ "@name").text
    report.system.version = (xml \ "system" \ "@version").text
    (xml \ "system" \ "props" \ "prop").foreach { ele => report.system.properties.put((ele \ "@name").text, (ele \ "@value").text) }

    (xml \ "modules" \ "module").foreach { ele => parseModule(ele, report, None) }
    (xml \ "pages" \ "page").foreach { ele =>
      report.addPage(
        new Page((ele \ "@name").text, (ele \ "@iterator").text))
    }
    report.template = (xml \ "pages" \ "@template").text
    report.extension = (xml \ "pages" \ "@extension").text
    report.imageurl = (xml \ "pages" \ "@imageurl").text
    report.init()
    report
  }

  def parseModule(node: scala.xml.Node, report: Report, parent: Option[Module]) {
    val module = new Module((node \ "@name").text, (node \ "@title").text, (node \ "@tables").text)
    (node \ "image").foreach { ele =>
      module.addImage(
        new Image((ele \ "@name").text, (ele \ "@title").text, (ele \ "@tables").text, ele.text.trim))
    }
    parent match {
      case None => report.addModule(module)
      case Some(p) => p.addModule(module)
    }

    (node \ "module").foreach { ele => parseModule(ele, report, Some(module)) }
  }
}

class Report(val dbconf: DbConfig) extends Initializing {

  var title: String = _

  var system: System = new System

  var modules: List[Module] = List()

  var pages: List[Page] = List()

  var template: String = _

  var imageurl: String = _

  var extension: String = _

  def images: List[Image] = {
    val buf = new collection.mutable.ListBuffer[Image]
    for (module <- modules)
      buf ++= module.allImages
    buf.toList
  }

  def addModule(module: Module) {
    modules :+= module
  }

  def addPage(page: Page) {
    pages :+= page
  }

  def init() {
    if (Strings.isEmpty(template)) template = "html"
    if (Strings.isEmpty(title)) title = "数据库结构说明"
    if (Strings.isEmpty(imageurl)) imageurl = "images/"
    else {
      if (!imageurl.endsWith("/")) imageurl += "/"
    }
    if (Strings.isEmpty(extension)) extension = ".html"
  }
}
