package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io. StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

class MapConverter(mapper: Mapper) extends AbstractIterableConverter[Map[Object, Object]](mapper) {

  def marshal(source: Map[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val entryName = mapper.serializedClass(classOf[Tuple2[_, _]])
    source.foreach { item =>
      writer.startNode(entryName, item.getClass())
      writeItem(item._1, writer,context)
      writeItem(item._2,writer,context)
      writer.endNode();
    }
  }
}