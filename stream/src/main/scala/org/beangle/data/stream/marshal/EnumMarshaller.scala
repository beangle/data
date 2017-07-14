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
package org.beangle.data.stream.marshal

import org.beangle.data.stream.io.StreamWriter
import org.beangle.data.stream.marshal.Type.Type

class EnumMarshaller extends Marshaller[Enumeration#Value] {

  var ordinal = false
  
  def marshal(source: Enumeration#Value, writer: StreamWriter, context: MarshallingContext): Unit = {
    if (ordinal) writer.setValue(String.valueOf(source.id))
    else writer.setValue(source.toString)
  }

  override def targetType: Type = {
    Type.String
  }
}