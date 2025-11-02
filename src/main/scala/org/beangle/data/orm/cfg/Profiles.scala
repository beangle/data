/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.orm.cfg

import org.beangle.commons.config.Resources
import org.beangle.commons.lang.Strings.*
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.commons.lang.{ClassLoaders, Strings}
import org.beangle.commons.logging.Logging
import org.beangle.data.orm.{MappingModule, NamingPolicy}

import java.net.URL
import scala.collection.mutable

class Profiles(resources: Resources) extends Logging {

  private val defaultProfile = new MappingProfile

  private val profiles = new collection.mutable.HashMap[String, MappingProfile]

  private val namings = new collection.mutable.HashMap[String, NamingPolicy]

  var modules: List[MappingModule] = _

  init()

  def getSchema(clazz: Class[_]): Option[String] = {
    getProfile(clazz).getSchema(clazz)
  }

  def getPrefix(clazz: Class[_]): String = {
    getProfile(clazz).getPrefix(clazz)
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

  def getNamingPolicy(clazz: Class[_]): NamingPolicy = {
    getProfile(clazz).naming
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

  private def globalSchema: Option[String] = {
    val s = System.getProperty("beangle.data.orm.global_schema")
    if (Strings.isBlank(s)) None else Some(s.trim)
  }

  private def init(): Unit = {
    val rails = new RailsNamingPolicy(this)
    namings.put("rails", rails)
    namings.put("ejb3", new EJB3NamingPolicy(this))
    defaultProfile.naming = rails
    globalSchema foreach { s =>
      defaultProfile._schema = Some(s)
    }
    val ms = new mutable.HashMap[String, MappingModule]
    for (url <- resources.paths) addXMLConfig(url, ms)
    if (logger.isDebugEnabled) {
      if (profiles.nonEmpty) logger.debug(s"Table name pattern: -> \n${this.toString}")
    }
    //module排序,使其处理过程稳定化
    modules = ms.values.toSeq.sortBy(_.getClass.getName).toList
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
              profile.parent = profiles.get(parentName)
            }
            val len = parentName.length
            parentName = substringBeforeLast(parentName, ".")
            if (parentName.length() == len) parentName = ""
          }
      }
    }
  }

  private def addXMLConfig(url: URL, ms: mutable.HashMap[String, MappingModule]): Unit = {
    try {
      logger.debug(s"loading xml $url")
      val is = url.openStream()
      if (null != is) {
        val xml = scala.xml.XML.load(is)
        (xml \ "orm" \ "naming" \ "profile") foreach { ele => parseProfile(ele, null) }
        (xml \ "orm" \ "mapping") foreach { ele =>
          val name = (ele \ "@name").text
          val clz = (ele \ "@class").text
          if (ms.contains(clz)) {
            logger.warn("duplicated moudule " + clz)
          } else {
            val module = Reflections.getInstance[MappingModule](clz)
            if Strings.isNotBlank(name) then module.name = Some(name.trim())
            ms.put(clz, module)
          }
        }
        is.close()
      }
      autoWire()
    } catch {
      case e: Exception => throw new RuntimeException("property load error in url:" + url, e)
    }
  }

  private def parseProfile(melem: scala.xml.Node, parent: MappingProfile): Unit = {
    val profile = new MappingProfile
    if ((melem \ "@package").nonEmpty) {
      profile.packageName = (melem \ "@package").text
      if (null != parent) profile.packageName = parent.packageName + "." + profile.packageName
    }
    (melem \ "annotation") foreach { anElem =>
      val clazz = ClassLoaders.load((anElem \ "@class").text)
      val value = (anElem \ "@value").text
      val annModule = new AnnotationModule(clazz, value)
      profile._annotations += annModule

      if ((anElem \ "@schema").nonEmpty) {
        annModule.schema = parseSchema((anElem \ "@schema").text)
      }
      if ((anElem \ "@prefix").nonEmpty) annModule.prefix = (anElem \ "@prefix").text
    }
    if ((melem \ "@schema").nonEmpty) {
      profile._schema = Some(parseSchema((melem \ "@schema").text))
    }
    if ((melem \ "@abbrs").nonEmpty) {
      profile.abbrs = parseAbbreviations((melem \ "@abbrs").text)
    }

    if ((melem \ "@prefix").nonEmpty) profile._prefix = Some((melem \ "@prefix").text)
    val naming = if ((melem \ "@naming").nonEmpty) (melem \ "@naming").text else "rails"
    if (namings.contains(naming)) {
      profile.naming = namings(naming)
    } else {
      try {
        val policyClzz = ClassLoaders.load(naming)
        val policy = policyClzz.getConstructor(classOf[Profiles]).newInstance(this).asInstanceOf[NamingPolicy]
        namings.put(naming, policy)
        profile.naming = policy
      } catch {
        case e: Exception => throw new RuntimeException("Cannot find naming policy :" + naming + " due to " + e.getMessage)
      }
    }
    profiles.put(profile.packageName, profile)
    profile.parent = Option(parent)
    (melem \ "profile") foreach { child => parseProfile(child, profile) }
  }

  private def parseSchema(name: String): String = {
    globalSchema match {
      case None =>
        if (isEmpty(name) || (-1 == name.indexOf('{'))) return name
        val newName = replace(name, "$", "")
        val propertyName = substringBetween(newName, "{", "}")
        val pv = System.getProperty(propertyName)
        replace(newName, "{" + propertyName + "}", if (pv == null) "" else pv)
      case Some(n) => n
    }
  }

  private def parseAbbreviations(text: String): Map[String, String] = {
    val map = new mutable.HashMap[String, String]
    val abbrs = Strings.split(text)
    for (abbr <- abbrs) {
      val abbrPair = Strings.split(abbr, "=")
      if (abbrPair.length == 1) {
        map.put(abbrPair(0).trim(), "")
      } else {
        map.put(abbrPair(0).trim(), abbrPair(1).trim)
      }
    }
    map.toMap
  }
}
