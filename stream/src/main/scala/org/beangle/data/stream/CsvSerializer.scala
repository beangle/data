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
package org.beangle.data.stream

import org.beangle.commons.activation.MimeTypes
import org.beangle.commons.collection.page.Page
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.stream.io.StreamWriter
import org.beangle.data.stream.io.csv.{ CsvDriver, DefaultCsvDriver }
import org.beangle.data.stream.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.stream.marshal.{ DefaultMarshallerRegistry, MarshallerRegistry, MarshallingContext }

import javax.activation.MimeType

object CsvSerializer {
  def apply(): CsvSerializer = {
    val mapper = new DefaultMapper
    val registry = new DefaultMarshallerRegistry(mapper)
    new CsvSerializer(new DefaultCsvDriver(), mapper, registry)
  }
}

final class CsvSerializer(val driver: CsvDriver, val mapper: Mapper, val registry: MarshallerRegistry)
    extends AbstractSerializer {

  def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.TextCsv)
  }

  override def serialize(item: Any, writer: StreamWriter, params: Map[String, Any]): Unit = {
    val datas = item match {
      case null          => null
      case page: Page[_] => page.items
      case _             => item
    }

    val context = new MarshallingContext(this, writer, registry, params)
    writer.start(context)
    if (datas == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      writer.startNode(mapper.serializedClass(datas.getClass()), datas.getClass())
      context.marshal(datas, null)
    }
    writer.endNode()
    writer.end(context)
  }

  override def marshalNull(obj: Any, property: String, context: MarshallingContext): Unit = {
    val size = context.getProperties(BeanInfos.get(obj.getClass).properties.get(property).get.clazz).size
    if (size > 0) {
      (0 until size) foreach (i => context.writer.setValue(""))
    } else {
      context.writer.setValue("")
    }
  }

  override def hierarchical: Boolean = {
    false
  }
}
