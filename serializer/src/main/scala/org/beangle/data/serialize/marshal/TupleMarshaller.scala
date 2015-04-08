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

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

import Type.Type

class TupleConvertor(mapper: Mapper) extends Marshaller[Product] {
  def marshal(source: Product, writer: StreamWriter, context: MarshallingContext): Unit = {
    val iter = source.productIterator
    var i = 0
    writer.addAttribute("class", "tuple");
    while (iter.hasNext) {
      val item = iter.next
      i = i + 1
      writeItem(item.asInstanceOf[AnyRef], writer, context, i)
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    clazz.getSimpleName().startsWith("Tuple")
  }

  protected def writeItem(item: AnyRef, writer: StreamWriter, context: MarshallingContext, index: Int) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
      writer.addAttribute("index", String.valueOf(index))
    } else {
      val name = mapper.serializedClass(realitem.getClass)
      writer.startNode(name, realitem.getClass)
      writer.addAttribute("index", String.valueOf(index))
      context.marshal(realitem)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}