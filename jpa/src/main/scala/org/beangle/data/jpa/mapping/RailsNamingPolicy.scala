/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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

  private val profiles = new collection.mutable.HashMap[String, TableProfile]

  private val schemas = new collection.mutable.HashSet[String]

  //For information display
  val configLocations = new collection.mutable.HashSet[URL]
  /**
   * adjust parent relation by package name
   */
  def autoWire(): Unit = {
    if (profiles.size > 1) {
      profiles.foreach {
        case (key, profile) =>
          var parentName = substringBeforeLast(key, ".")
          while (isNotEmpty(parentName) && null == profile.parent) {
            if (profiles.contains(parentName) && profile.packageName != parentName) {
              logger.debug(s"set ${profile.packageName}'s parent is $parentName")
              profile.parent = profiles(parentName)
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
      logger.debug(s"loading $url")
      val is = url.openStream()
      if (null != is) {
        configLocations.add(url)
        (scala.xml.XML.load(is) \ "naming" \ "profile") foreach { ele => parseProfile(ele, null) }
        is.close()
      }
      autoWire()
    } catch {
      case e: IOException => logger.error("property load error", e)
    }
  }

  private def parseProfile(melem: scala.xml.Node, parent: TableProfile): Unit = {
    val profile = new TableProfile
    if (!(melem \ "@package").isEmpty) {
      profile.packageName = (melem \ "@package").text
      if (null != parent) profile.packageName = parent.packageName + "." + profile.packageName
    }
    (melem \ "class") foreach { anElem =>
      val clazz = ClassLoaders.load((anElem \ "@annotation").text)
      val value = (anElem \ "@value").text
      val annModule = new AnnotationModule(clazz, value)
      profile._annotations += annModule

      if (!(anElem \ "@schema").isEmpty) {
        annModule.schema = parseSchema((anElem \ "@schema").text)
        schemas += annModule.schema
      }
      if (!(anElem \ "@prefix").isEmpty) annModule.prefix = (anElem \ "@prefix").text
    }
    if (!(melem \ "@schema").isEmpty) {
      profile._schema = parseSchema((melem \ "@schema").text)
      schemas += profile._schema
    }
    if (!(melem \ "@prefix").isEmpty) profile._prefix = (melem \ "@prefix").text
    if (!(melem \ "@pluralize").isEmpty) profile.pluralize = (melem \ "@pluralize").text == "true"
    profiles.put(profile.packageName, profile)
    profile.parent = parent
    (melem \ "profile") foreach { child => parseProfile(child, profile) }
  }

  def getSchema(clazz: Class[_]): Option[String] = {
    getProfile(clazz) match {
      case None => None
      case Some(profile) => {
        var schema = profile.schema
        val anno = profile.annotations find { ann =>
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
    getSchema(ClassLoaders.load(className))
  }

  def getPrefix(clazz: Class[_]): String = {
    getProfile(clazz) match {
      case None => ""
      case Some(profile) => {
        var prefix = profile.prefix
        val anno = profile.annotations find { ann =>
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

  def getProfile(clazz: Class[_]): Option[TableProfile] = {
    var name = clazz.getName()
    var matched: Option[TableProfile] = None
    while (isNotEmpty(name) && matched == None) {
      if (profiles.contains(name)) matched = Some(profiles(name))
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
      if (!profiles.isEmpty) logger.info(s"Table name pattern: -> ${this.toString}")
    }
  }

  def classToTableName(clazzName: String): String = {
    val className = if (clazzName.endsWith("Bean")) substringBeforeLast(clazzName, "Bean") else clazzName
    var tableName = addUnderscores(unqualify(className))
    if (null != pluralizer) tableName = pluralizer.pluralize(tableName)
    val clazz: Class[_] = try {
      ClassLoaders.load(className)
    } catch {
      case e: ClassNotFoundException => if (clazzName != className) ClassLoaders.load(clazzName) else throw e
      case e: Throwable              => throw e
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
    //    getModule(ClassLoaders.load(className)) foreach { p =>
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
    val pv = System.getProperty(propertyName)
    Strings.replace(newName, "{" + propertyName + "}", if (pv == null) "" else pv)
  }

  override def toString: String = {
    if (profiles.isEmpty) return ""
    val maxlength = profiles.map(m => m._1.length).max
    val sb = new StringBuilder()
    profiles foreach {
      case (packageName, profile) =>
        sb.append(rightPad(packageName, maxlength, ' ')).append(" : [")
          .append(profile.schema.getOrElse(""))
        sb.append(",").append(profile.prefix)
        //      if (!module.abbreviations.isEmpty()) {
        //        sb.append(" , ").append(module.abbreviations)
        //      }
        sb.append(']').append(';')
    }
    if (sb.length > 0) sb.deleteCharAt(sb.length - 1)
    sb.toString()
  }
  protected def addUnderscores(name: String): String = unCamel(name.replace('.', '_'), '_')

  class TableProfile {
    var packageName: String = _
    var pluralize: Boolean = _
    var _schema: String = _
    var _prefix: String = _
    var parent: TableProfile = _

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


