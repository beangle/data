package org.beangle.data.serializer.marshal.converter

import java.{ util => ju }
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

class CollectionConverter(mapper: Mapper) extends AbstractCollectionConverter[ju.Collection[Object]](mapper) {

  def marshal(source: ju.Collection[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val iterator = source.iterator()
    while (iterator.hasNext) {
      writeItem(iterator.next(), writer, context)
    }
  }
}