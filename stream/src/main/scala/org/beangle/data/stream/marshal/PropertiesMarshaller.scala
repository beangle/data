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

import java.{ util => ju }

import org.beangle.commons.collection.Properties
import org.beangle.data.stream.io.StreamWriter
import org.beangle.data.stream.mapper.Mapper
import org.beangle.data.stream.marshal.Type.Type

class PropertiesMarshaller(val mapper: Mapper) extends Marshaller[ju.Properties] {

  def marshal(source: ju.Properties, writer: StreamWriter, context: MarshallingContext): Unit = {
    val enum = source.propertyNames()
    while (enum.hasMoreElements) {
      val key = enum.nextElement.asInstanceOf[String]
      val value = source.getProperty(key)
      if (null != value) {
        writer.startNode(mapper.serializedMember(source.getClass(), key), value.getClass)
        context.marshal(value)
        writer.endNode()
      }
    }
  }

  override def targetType: Type = {
    Type.Object
  }

}

class JsonObjectMarshaller(val mapper: Mapper) extends Marshaller[Properties] {

  def marshal(source: Properties, writer: StreamWriter, context: MarshallingContext): Unit = {
    val enum = source.keys.iterator
    while (enum.hasNext) {
      val key = enum.next()
      val value = source(key).asInstanceOf[AnyRef]
      if (null != value) {
        writer.startNode(mapper.serializedMember(source.getClass(), key), value.getClass)
        context.marshal(value)
        writer.endNode()
      }
    }
  }

  override def targetType: Type = {
    Type.Object
  }

}