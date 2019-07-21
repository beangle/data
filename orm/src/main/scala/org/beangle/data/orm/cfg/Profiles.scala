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
package org.beangle.data.orm.cfg

import java.net.URL

import org.beangle.commons.config.Resources
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.Strings._
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.commons.logging.Logging
import org.beangle.data.orm.{MappingModule, NamingPolicy}

class Profiles(resources: Resources) extends Logging {

  val defaultProfile = new MappingProfile

  defaultProfile.naming = new RailsNamingPolicy(this)

  private val profiles = new collection.mutable.HashMap[String, MappingProfile]

  private val namings = new collection.mutable.HashMap[String, NamingPolicy]

  val modules = new collection.mutable.HashSet[MappingModule]

  namings.put("rails", new RailsNamingPolicy(this))

  for (url <- resources.paths) addConfig(url)
  if (profiles.nonEmpty) logger.info(s"Table name pattern: -> \n${this.toString}")

  def addConfig(url: URL): Unit = {
    try {
      logger.debug(s"loading $url")
      val is = url.openStream()
      if (null != is) {
        val xml = scala.xml.XML.load(is)
        (xml \ "naming" \ "profile") foreach { ele => parseProfile(ele, null) }
        (xml \ "mapping") foreach { ele =>
          modules += Reflections.getInstance[MappingModule]((ele \ "@class").text)
        }
        is.close()
      }
      autoWire()
    } catch {
      case e: Exception => logger.error("property load error in url:" + url, e)
    }
  }

  def getSchema(clazz: Class[_]): Option[String] = {
    val profile = getProfile(clazz)
    var schema = profile.schema
    val anno = profile.annotations find { ann =>
      clazz.getAnnotations exists { annon =>
        if (ann.clazz.isAssignableFrom(annon.getClass)) {
          if (isNotEmpty(ann.value)) {
            try {
              val method = annon.getClass.getMethod("value")
              String.valueOf(method.invoke(annon)) == ann.value
            } catch {
              case _: Throwable =>
                Console.err.print("Annotation value needed:", ann.value, annon.getClass)
                false
            }
          } else true
        } else false
      }
    }
    anno foreach (an => if (isNotEmpty(an.schema)) schema = Some(an.schema))
    schema
  }

  def getPrefix(clazz: Class[_]): String = {
    val profile = getProfile(clazz)
    var prefix = profile.prefix
    val anno = profile.annotations find { ann =>
      clazz.getAnnotations exists { annon =>
        if (ann.clazz.isAssignableFrom(annon.getClass)) {
          if (isNotEmpty(ann.value)) {
            try {
              val method = annon.getClass.getMethod("value")
              String.valueOf(method.invoke(annon)) == ann.value
            } catch {
              case _: Exception =>
                Console.err.print("Annotation value needed:", ann.value, annon.getClass)
                false
            }
          } else true
        } else false
      }
    }
    anno foreach (an => if (isNotEmpty(an.prefix)) prefix = an.prefix)
    if (isEmpty(prefix)) "" else prefix
  }

  def getNamingPolicy(clazz: Class[_]): NamingPolicy = {
    getProfile(clazz).naming
  }

  def getProfile(clazz: Class[_]): MappingProfile = {
    var name = clazz.getName
    var matched: Option[MappingProfile] = None
    while (isNotEmpty(name) && matched.isEmpty) {
      if (profiles.contains(name)) matched = Some(profiles(name))
      val len = name.length
      name = substringBeforeLast(name, ".")
      if (name.length() == len) name = ""
    }
    matched.getOrElse(defaultProfile)
  }

  /**
    * adjust parent relation by package name
    */
  private def autoWire(): Unit = {
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

  private def parseProfile(melem: scala.xml.Node, parent: MappingProfile): Unit = {
    val profile = new MappingProfile
    if ((melem \ "@package").nonEmpty) {
      profile.packageName = (melem \ "@package").text
      if (null != parent) profile.packageName = parent.packageName + "." + profile.packageName
    }
    (melem \ "class") foreach { anElem =>
      val clazz = ClassLoaders.load((anElem \ "@annotation").text)
      val value = (anElem \ "@value").text
      val annModule = new AnnotationModule(clazz, value)
      profile._annotations += annModule

      if ((anElem \ "@schema").nonEmpty) {
        annModule.schema = parseSchema((anElem \ "@schema").text)
      }
      if ((anElem \ "@prefix").nonEmpty) annModule.prefix = (anElem \ "@prefix").text
    }
    if ((melem \ "@schema").nonEmpty) {
      profile._schema = parseSchema((melem \ "@schema").text)
    }
    if ((melem \ "@prefix").nonEmpty) profile._prefix = (melem \ "@prefix").text
    val naming = if ((melem \ "@naming").nonEmpty) (melem \ "@naming").text else "rails"
    if (namings.contains(naming)) {
      profile.naming = namings(naming)
    } else {
      throw new RuntimeException("Cannot find naming policy :" + naming)
    }
    profiles.put(profile.packageName, profile)
    profile.parent = parent
    (melem \ "profile") foreach { child => parseProfile(child, profile) }
  }

  private def parseSchema(name: String): String = {
    if (isEmpty(name) || (-1 == name.indexOf('{'))) return name
    val newName = replace(name, "$", "")
    val propertyName = substringBetween(newName, "{", "}")
    val pv = System.getProperty(propertyName)
    replace(newName, "{" + propertyName + "}", if (pv == null) "" else pv)
  }

  override def toString: String = {
    if (profiles.isEmpty) return ""
    val maxlength = profiles.keys.map(_.length).max
    val sb = new StringBuilder
    profiles.keySet.toList.sorted foreach { packageName =>
      val profile = profiles(packageName)
      sb.append(rightPad(packageName, maxlength, ' ')).append(" : [")
        .append(profile.schema.getOrElse("_")).append(",")
      sb.append(if (isEmpty(profile.prefix)) "_" else profile.prefix)
      //      if (!module.abbreviations.isEmpty()) {
      //        sb.append(" , ").append(module.abbreviations)
      //      }
      sb.append(']').append("\n")
    }
    if (sb.nonEmpty) sb.deleteCharAt(sb.length - 1)
    sb.toString
  }
}
