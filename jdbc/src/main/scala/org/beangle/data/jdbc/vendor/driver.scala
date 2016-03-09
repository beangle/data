/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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
package org.beangle.data.jdbc.vendor

import java.util.regex.Pattern

import org.beangle.commons.lang.Strings.replace

object Driver {
  def apply(prefix: String, dataSourceClassName: String, className: String, urlformats: String*): DriverInfo = {
    new DriverInfo(dataSourceClassName, className, prefix, urlformats)
  }
}

class DriverInfo(val dataSourceClassName: String, val className: String, val prefix: String, val urlformats: Seq[String]) {
  var vendor: VendorInfo = _
}

class UrlFormat(val format: String) {
  val params: List[String] = findParams(format)

  private def findParams(format: String): List[String] = {
    val m = Pattern.compile("(<.*?>)").matcher(format)
    val ps = new collection.mutable.ListBuffer[String]
    while (m.find()) {
      val matched = m.group(0)
      ps += matched.substring(1, matched.length - 1)
    }
    ps.toList
  }

  def fill(values: Map[String, String]): String = {
    var result = format
    for ((k, v) <- values) result = replace(result, "<" + k + ">", v)
    result = replace(result, "[", "")
    result = replace(result, "]", "")
    result
  }
}