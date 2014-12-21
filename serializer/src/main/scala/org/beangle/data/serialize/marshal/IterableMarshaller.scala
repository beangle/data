package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

class IterableMarshaller(mapper: Mapper) extends AbstractIterableMarshaller[Iterable[Object]](mapper) {

  def marshal(source: Iterable[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    source.foreach { item =>
      writeItem(item, writer, context)
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    !classOf[collection.Map[_, _]].isAssignableFrom(clazz)
  }
}