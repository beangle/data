/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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

import org.beangle.commons.collection.page.Page
import org.beangle.commons.lang.Primitives
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.data.serialize.marshal.Type.Type

class PageMarshaller(val mapper: Mapper) extends Marshaller[Page[Object]] {

  def marshal(source: Page[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    val properties = context.getProperties(sourceType)
    val getters = BeanManifest.get(sourceType).readables
    properties foreach { property =>
      val itemType = Primitives.wrap(getters(property).clazz)
      writer.startNode(mapper.serializedMember(source.getClass, property), itemType)
      property match {
        case "pageIndex" =>
          context.marshal(Integer.valueOf(source.pageIndex))
        case "pageSize" =>
          context.marshal(Integer.valueOf(source.pageSize))
        case "totalPages" =>
          context.marshal(Integer.valueOf(source.totalPages))
        case "totalItems" =>
          context.marshal(Integer.valueOf(source.totalItems))
        case "items" =>
          context.marshal(source.items)
        case other: String => context.marshal(getters(other).getter.get.invoke(source))
      }
      writer.endNode()
    }
  }

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      val name = mapper.serializedClass(realitem.getClass())
      writer.startNode(name, realitem.getClass())
      context.marshal(realitem)
    }
    writer.endNode()
  }

  override def support(clazz: Class[_]): Boolean = {
    (classOf[Page[_]].isAssignableFrom(clazz))
  }

  override def targetType: Type = {
    Type.Object
  }
}