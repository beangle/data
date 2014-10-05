package org.beangle.data.serializer.marshal

import org.beangle.data.serializer.converter.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.io.StreamWriter
import java.{ util => ju }
import org.beangle.data.serializer.io.Path
import org.beangle.data.serializer.io.PathStack
import org.beangle.commons.collection.IdentityCache

class MarshallingContext(val marshaller: Marshaller, val writer: StreamWriter, val registry: ConverterRegistry) {

  val references = new IdentityCache[AnyRef, Id]

  def convert(item: Object, writer: StreamWriter): Unit = {
    convert(item, writer, null)
  }

  def convert(item: Object, writer: StreamWriter, converter: Converter[Object]): Unit = {
    if (converter == null) {
      marshaller.convert(item, writer, registry.lookup(item.getClass.asInstanceOf[Class[Object]]), this)
    } else {
      marshaller.convert(item, writer, converter, this)
    }
  }

  def lookupReference(item: Object): Id = {
    references.get(item)
  }
}

class Id(val key: AnyRef, val path: Path)
