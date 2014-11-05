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
package org.beangle.data.conversion.impl

import org.beangle.commons.lang.Strings

class ObjectFilter extends Filter {

  val excludes = new collection.mutable.ListBuffer[String]

  val includes = new collection.mutable.ListBuffer[String]

  def filter(tables: Iterable[String]): List[String] = {
    val results = new collection.mutable.ListBuffer[String]
    for (tabame <- tables) {
      val tableName = (if (tabame.contains(".")) Strings.substringAfter(tabame, ".") else tabame).toLowerCase()

      if (includes.isEmpty || 
          (includes.exists(p => p == "*" || tableName.startsWith(p)) && !excludes.contains(tableName)))
        results += tabame
    }
    results.toList
  }

  def exclude(table: String) {
    excludes += table.toLowerCase()
  }

  def include(table: String) {
    includes += table.toLowerCase()
  }

}