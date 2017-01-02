/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
package org.beangle.data.serialize.io.json

import java.io.Writer
import org.beangle.data.serialize.io.{ AbstractDriver, StreamWriter }
import org.beangle.commons.io.BufferedWriter

class DefaultJsonDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) with JsonDriver {

  def createWriter(out: Writer, params: Map[String, Any]): StreamWriter = {
    if (params.contains("pretty")) new PrettyJsonWriter(new BufferedWriter(out), registry)
    else new DefaultJsonWriter(new BufferedWriter(out), registry)
  }
}