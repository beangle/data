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

import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.Strings.{substringBeforeLast, unCamel}
import org.beangle.commons.logging.Logging
import org.beangle.commons.text.inflector.Pluralizer
import org.beangle.commons.text.inflector.en.EnNounPluralizer
import org.beangle.data.orm.{Name, NamingPolicy}

/**
 * 根据报名动态设置schema,prefix名字
 *
 * @author chaostone
 */
class RailsNamingPolicy(profiles: Profiles) extends NamingPolicy with Logging {

  private val pluralizer: Pluralizer = EnNounPluralizer

  override def classToTableName(clazz: Class[_], entityName: String): Name = {
    var className = if (clazz.getName.endsWith("Bean")) substringBeforeLast(clazz.getName, "Bean") else clazz.getName
    val p = profiles.getProfile(clazz)
    for ((k, v) <- p.abbrs) className = Strings.replace(className, k, v)
    val tableName = addUnderscores(unqualify(className))
    Name(p.getSchema(clazz), p.getPrefix(clazz) + pluralizer.pluralize(tableName))
  }

  override def collectionToTableName(clazz: Class[_], entityName: String, tableName: String, collectionName: String): Name = {
    var simpleName = unqualify(collectionName)
    val p = profiles.getProfile(clazz)
    for ((k, v) <- p.abbrs) simpleName = Strings.replace(simpleName, k, v)
    val collectionTableName = tableName + "_" + addUnderscores(simpleName)
    Name(p.getSchema(clazz), collectionTableName)
  }

  override def propertyToColumnName(clazz: Class[_], property: String): String = {
    if (property.charAt(0).isUpper) {
      val p = profiles.getProfile(clazz)
      var pName = property
      for ((k, v) <- p.abbrs) pName = Strings.replace(pName, k, v)
      addUnderscores(pName)
    } else {
      addUnderscores(property)
    }
  }

  private def unqualify(qualifiedName: String): String = {
    val loc = qualifiedName.lastIndexOf('.')
    if (loc < 0) qualifiedName else qualifiedName.substring(loc + 1)
  }

  private def addUnderscores(name: String): String = unCamel(name.replace('.', '_'), '_')
}
