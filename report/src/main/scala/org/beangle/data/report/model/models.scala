package org.beangle.data.report.model

import scala.Array.canBuildFrom

import org.beangle.data.jdbc.meta.Table
import org.beangle.commons.lang.Strings
import org.beangle.commons.text.regex.AntPathPattern

class Page(val name: String, val iterator: String)

trait TableContainer {
  val patterns: Array[AntPathPattern]
  val tables = new collection.mutable.ListBuffer[Table]

  def matches(tableName: String): Boolean = {
    val lowertable = tableName.toLowerCase
    var matched = false
    for (pattern <- patterns; if !matched) {
      if (pattern.matches(lowertable)) matched = true
    }
    matched
  }

  def addTable(table: Table) {
    tables += table
  }
}

class Image(val name: String, val title: String, tableseq: String, val description: String) extends TableContainer {
  override val patterns = Strings.split(tableseq.toLowerCase, ",").map(new AntPathPattern(_))

  def select(alltables: collection.Iterable[Table]) {
    for (table <- alltables) {
      if (matches(table.name)) addTable(table)
    }
  }
}

class Module(val name: String, val title: String, tableseq: String) extends TableContainer {
  val content = new StringBuffer();
  override val patterns = Strings.split(tableseq.toLowerCase, ",").map(new AntPathPattern(_))
  var children: List[Module] = List()
  var images: List[Image] = List()
  var parent: Option[Module] = None
  def addImage(image: Image) {
    images :+= image
  }

  override def toString = path + " tables(" + tables.size + ")"

  def path: String = if (parent.isEmpty) name else (parent.get.path + "/") + name

  def addModule(module: Module) {
    children :+= module
    module.parent = Some(this)
  }

  def allImages: List[Image] = {
    val buf = new collection.mutable.ListBuffer[Image]
    buf ++= images
    for (module <- children)
      buf ++= module.allImages
    buf.toList
  }

  def filter(alltables: collection.mutable.Set[Table]) {
    for (module <- children)
      module.filter(alltables)

    for (table <- alltables) {
      if (matches(table.name)) addTable(table)
    }

    alltables --= tables
  }
}

class System {
  var name: String = _
  var version: String = _
  val properties = new java.util.Properties
}
