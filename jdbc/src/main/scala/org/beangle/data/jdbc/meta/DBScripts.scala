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

package org.beangle.data.jdbc.meta

class DBScripts {
  var schemas: List[String] = _
  var tables: List[String] = _
  var sequences: List[String] = _
  var keys:List[String]=_
  var constraints: List[String] = _
  var indices: List[String] = _
  var comments: List[String] = _
  var auxiliaries: List[String] = _
  var warnings:List[String]=_
}
