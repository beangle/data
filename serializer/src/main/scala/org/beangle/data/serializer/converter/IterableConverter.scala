package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

class IterableConverter(mapper: Mapper) extends AbstractIterableConverter[Iterable[Object]](mapper) {

  def marshal(source: Iterable[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    source.foreach { item =>
      writeItem(item, writer, context)
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    !classOf[collection.Map[_, _]].isAssignableFrom(clazz)
  }
}