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
package org.beangle.data.stream.io.jsonp

import org.beangle.data.stream.marshal.MarshallingContext
import org.beangle.data.stream.marshal.MarshallerRegistry
import java.io.Writer
import org.beangle.data.stream.io.json.DefaultJsonWriter

class DefaultJsonpWriter(writer: Writer, registry: MarshallerRegistry) extends DefaultJsonWriter(writer, registry) {
  var callbackName = "callback"
  override def start(context: MarshallingContext): Unit = {
    val callback = context.params.get(callbackName).getOrElse("callback").asInstanceOf[String]
    writer.write(callback)
    writer.write('(')
  }

  override def end(context: MarshallingContext): Unit = {
    writer.write(')')
  }
}