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
package org.beangle.data.jpa.mapping

import java.io.IOException
import java.net.URL
import org.beangle.commons.inject.Resources
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.Strings.{ isNotEmpty, rightPad, substringBeforeLast, unCamel }
import org.beangle.commons.logging.Logging
import org.beangle.commons.text.inflector.Pluralizer
import org.beangle.commons.text.inflector.en.EnNounPluralizer
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.SystemInfo

/**
 * 根据报名动态设置schema,prefix名字
 *
 * @author chaostone
 */
class RailsNamingPolicy extends NamingPolicy with Logging {

  /** 实体表表名长度限制 */
  var entityTableMaxLength = 30

  /** 关联表表名长度限制 */
  var relationTableMaxLength = 30

  private var pluralizer: Pluralizer = new EnNounPluralizer()

  private val modules = new collection.mutable.HashMap[String, TableModule]

  private val schemas = new collection.mutable.HashSet[String]

  //For information display
  val configLocations = new collection.mutable.HashSet[URL]
  /**
   * adjust parent relation by package name
   */
  def autoWire(): Unit = {
    if (modules.size > 1) {
      modules.foreach {
        case (key, module) =>
          var parentName = substringBeforeLast(key, ".")
          while (isNotEmpty(parentName) && null == module.parent) {
            if (modules.contains(parentName) && module.packageName != parentName) {
              debug(s"set ${module.packageName}'s parent is $parentName")
              module.parent = modules(parentName)
            }
            val len = parentName.length
            parentName = substringBeforeLast(parentName, ".")
            if (parentName.length() == len) parentName = ""
          }
      }
    }
  }

  def addConfig(url: URL): Unit = {
    try {
      debug(s"loading $url")
      val is = url.openStream()
      if (null != is) {
        configLocations.add(url)
        (scala.xml.XML.load(is) \ "module") foreach { ele => parseModule(ele, null) }
        is.close()
      }
      autoWire()
    } catch {
      case e: IOException => error("property load error", e)
    }
  }

  private def parseModule(melem: scala.xml.Node, parent: TableModule): Unit = {
    val module = new TableModule
    if (!(melem \ "@package").isEmpty) {
      module.packageName = (melem \ "@package").text
      if (null != parent) module.packageName = parent.packageName + "." + module.packageName
    }
    (melem \ "class") foreach { anElem =>
      val clazz = ClassLoaders.loadClass((anElem \ "@annotation").text)
      val value = (anElem \ "@value").text
      val annModule = new AnnotationModule(clazz, value)
      module._annotations += annModule

      if (!(anElem \ "@schema").isEmpty) {
        annModule.schema = parseSchema((anElem \ "@schema").text)
        schemas += annModule.schema
      }
      if (!(anElem \ "@prefix").isEmpty) annModule.prefix = (anElem \ "@prefix").text
    }
    if (!(melem \ "@schema").isEmpty) {
      module._schema = parseSchema((melem \ "@schema").text)
      schemas += module._schema
    }
    if (!(melem \ "@prefix").isEmpty) module._prefix = (melem \ "@prefix").text
    if (!(melem \ "@pluralize").isEmpty) module.pluralize = (melem \ "@pluralize").text == "true"
    modules.put(module.packageName, module)
    module.parent = parent
    (melem \ "module") foreach { child => parseModule(child, module) }
  }

  def getSchema(clazz: Class[_]): Option[String] = {
    getModule(clazz) match {
      case None => None
      case Some(module) => {
        var schema = module.schema
        val anno = module.annotations find { ann =>
          clazz.getAnnotations() exists { annon =>
            if (ann.clazz.isAssignableFrom(annon.getClass())) {
              if (Strings.isNotEmpty(ann.value)) {
                try {
                  val method = annon.getClass().getMethod("value")
                  String.valueOf(method.invoke(annon)) == ann.value
                } catch {
                  case e: Throwable => {
                    Console.err.print("Annotation value needed:", ann.value, annon.getClass)
                    false
                  }
                }
              } else true
            } else false
          }
        }
        anno foreach (an => if (Strings.isNotEmpty(an.schema)) schema = Some(an.schema))
        schema
      }
    }
  }

  def getSchema(className: String): Option[String] = {
    getSchema(ClassLoaders.loadClass(className))
  }

  def getPrefix(clazz: Class[_]): String = {
    getModule(clazz) match {
      case None => ""
      case Some(module) => {
        var prefix = module.prefix
        val anno = module.annotations find { ann =>
          clazz.getAnnotations() exists { annon =>
            if (ann.clazz.isAssignableFrom(annon.getClass())) {
              if (Strings.isNotEmpty(ann.value)) {
                try {
                  val method = annon.getClass().getMethod("value")
                  String.valueOf(method.invoke(annon)) == ann.value
                } catch {
                  case e: Exception => {
                    Console.err.print("Annotation value needed:", ann.value, annon.getClass)
                    false
                  }
                }
              } else true
            } else false
          }
        }
        anno foreach (an => if (Strings.isNotEmpty(an.prefix)) prefix = an.prefix)
        if (Strings.isEmpty(prefix)) "" else prefix
      }
    }
  }

  def getModule(clazz: Class[_]): Option[TableModule] = {
    var name = clazz.getName()
    var matched: Option[TableModule] = None
    while (isNotEmpty(name) && matched == None) {
      if (modules.contains(name)) matched = Some(modules(name))
      val len = name.length
      name = substringBeforeLast(name, ".")
      if (name.length() == len) name = ""
    }
    matched
  }

  /**
   * is Multiple schema for entity
   */
  def multiSchema: Boolean = !schemas.isEmpty

  def setResources(resources: Resources) {
    if (null != resources) {
      for (url <- resources.paths) addConfig(url)
      if (!modules.isEmpty) info(s"Table name pattern: -> ${this.toString}")
    }
  }

  def classToTableName(clazzName: String): String = {
    val className = if (clazzName.endsWith("Bean")) substringBeforeLast(clazzName, "Bean") else clazzName
    var tableName = addUnderscores(unqualify(className))
    if (null != pluralizer) tableName = pluralizer.pluralize(tableName)
    val clazz: Class[_] = try {
      ClassLoaders.loadClass(className)
    } catch {
      case e: ClassNotFoundException => if (clazzName != className) ClassLoaders.loadClass(clazzName) else throw e
      case e: Throwable => throw e
    }
    tableName = getPrefix(clazz) + tableName
    //      if (tableName.length() > entityTableMaxLength) {
    //        for ((k, v) <- p.abbreviations)
    //          tableName = replace(tableName, k, v)
    //      }
    tableName
  }

  def collectionToTableName(className: String, tableName: String, collectionName: String): String = {
    var collectionTableName = tableName + "_" + addUnderscores(unqualify(collectionName))
    //    getModule(ClassLoaders.loadClass(className)) foreach { p =>
    //      if ((collectionTableName.length() > relationTableMaxLength)) {
    //        for ((k, v) <- p.abbreviations)
    //          collectionTableName = replace(collectionTableName, k, v)
    //      }
    //    }
    collectionTableName
  }

  protected def unqualify(qualifiedName: String): String = {
    val loc = qualifiedName.lastIndexOf('.')
    if (loc < 0) qualifiedName else qualifiedName.substring(loc + 1)
  }

  private def parseSchema(name: String): String = {
    if (Strings.isEmpty(name) || (-1 == name.indexOf('{'))) return name
    var newName = Strings.replace(name, "$", "")
    val propertyName = Strings.substringBetween(newName, "{", "}")
    val value = SystemInfo.properties.get(propertyName).getOrElse("")
    Strings.replace(newName, "{" + propertyName + "}", value)
  }

  override def toString: String = {
    if (modules.isEmpty) return ""
    val maxlength = modules.map(m => m._1.length).max
    val sb = new StringBuilder()
    modules foreach {
      case (packageName, module) =>
        sb.append(rightPad(packageName, maxlength, ' ')).append(" : [")
          .append(module.schema.getOrElse(""))
        sb.append(",").append(module.prefix)
        //      if (!module.abbreviations.isEmpty()) {
        //        sb.append(" , ").append(module.abbreviations)
        //      }
        sb.append(']').append(';')
    }
    if (sb.length > 0) sb.deleteCharAt(sb.length - 1)
    sb.toString()
  }
  protected def addUnderscores(name: String): String = unCamel(name.replace('.', '_'), '_')

  class TableModule {
    var packageName: String = _
    var pluralize: Boolean = _
    var _schema: String = _
    var _prefix: String = _
    var parent: TableModule = _

    def schema: Option[String] = {
      if (isNotEmpty(_schema)) Some(_schema)
      else if (null != parent) parent.schema
      else None
    }
    def prefix: String = {
      if (isNotEmpty(_prefix)) _prefix
      else if (null != parent) parent.prefix
      else ""
    }
    val _annotations = new collection.mutable.ListBuffer[AnnotationModule]

    def annotations: collection.Seq[AnnotationModule] = {
      if (_annotations.isEmpty && null != parent) parent._annotations
      else _annotations
    }

    override def toString(): String = {
      val sb = new StringBuilder()
      sb.append("[package:").append(packageName).append(", schema:").append(_schema)
      sb.append(", prefix:").append(_prefix).append(']')
      sb.toString()
    }
  }

  class AnnotationModule(val clazz: Class[_], val value: String) {
    var schema: String = _
    var prefix: String = _
  }
}


