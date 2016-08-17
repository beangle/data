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
package org.beangle.data.serialize.marshal

import java.beans.Transient

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

import Type.Type
import org.beangle.commons.lang.reflect.BeanInfos

class BeanMarshaller(val mapper: Mapper) extends Marshaller[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    val properties = context.getProperties(sourceType)
    if (!properties.isEmpty) {
      val getters = BeanInfos.get(sourceType).properties
      properties foreach { name =>
        val getter = getters(name)
        if (!getter.isTransient) {
          val value = extractOption(getter.getter.get.invoke(source))
          if (null != value) {
            writer.startNode(mapper.serializedMember(source.getClass, name), value.getClass)
            context.marshal(value)
            writer.endNode()
          } else {
            context.marshalNull(source, name)
          }
        }
      }
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    !(clazz.getName.startsWith("java.") || clazz.getName.startsWith("scala.") ||
      clazz.isArray || classOf[Seq[_]].isAssignableFrom(clazz) ||
      classOf[collection.Map[_, _]].isAssignableFrom(clazz))
  }

  override def targetType: Type = {
    Type.Object
  }
}
