package org.beangle.data.serialize.marshal

import java.{ util => ju }
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

class CollectionMarshaller(mapper: Mapper) extends AbstractCollectionMarshaller[ju.Collection[Object]](mapper) {

  def marshal(source: ju.Collection[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val iterator = source.iterator()
    while (iterator.hasNext) {
      writeItem(iterator.next(), writer, context)
    }
  }
}