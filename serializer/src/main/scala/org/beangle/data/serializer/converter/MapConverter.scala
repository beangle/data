package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

class MapConverter(mapper: Mapper) extends Converter[collection.Map[Object, Object]] {

  def marshal(source: collection.Map[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val entryName = "entry"
    source.foreach { item =>
      writer.startNode(entryName, item.getClass())
      writeItem(true, item._1, writer, context)
      writeItem(false, item._2, writer, context)
      writer.endNode();
    }
  }

  protected def writeItem(key: Boolean, item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      // todo: this is duplicated in TreeMarshaller.start()
      val name = if (key) "key" else "value"
      writer.startNode(name, classOf[Null])
    } else {
      val name = if (key) "key" else "value"
      writer.startNode(name, realitem.getClass())
      context.convert(realitem, writer)
    }
    writer.endNode()
  }
}