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

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.commons.collection.page.Page

class IterableMarshaller(mapper: Mapper) extends AbstractIterableMarshaller[Iterable[Object]](mapper) {

  def marshal(source: Iterable[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    source.foreach { item =>
      writeItem(item, writer, context)
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    (!classOf[collection.Map[_, _]].isAssignableFrom(clazz)) && (!(classOf[Page[_]].isAssignableFrom(clazz)))
  }
}