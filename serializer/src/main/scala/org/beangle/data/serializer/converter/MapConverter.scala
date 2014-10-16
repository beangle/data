package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext
import Type.Type

import java.{ util => ju }

abstract class AbstractMapConverter[T] extends Converter[T] {
  protected def writeItem(key: Boolean, item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      val name = if (key) "key" else "value"
      writer.startNode(name, classOf[Null])
    } else {
      val name = if (key) "key" else "value"
      writer.startNode(name, realitem.getClass())
      context.convert(realitem, writer)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}

class MapConverter(mapper: Mapper) extends AbstractMapConverter[collection.Map[Object, Object]] {

  def marshal(source: collection.Map[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val entryName = "entry"
    source.foreach { item =>
      writer.startNode(entryName, item.getClass())
      writeItem(true, item._1, writer, context)
      writeItem(false, item._2, writer, context)
      writer.endNode();
    }
  }
}

class JavaMapConverter(mapper: Mapper) extends AbstractMapConverter[ju.Map[Object, Object]] {

  def marshal(source: ju.Map[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val entryName = "entry"
    val iterator = source.entrySet().iterator()
    while (iterator.hasNext) {
      val item = iterator.next()
      writer.startNode(entryName, item.getClass())
      writeItem(true, item.getKey, writer, context)
      writeItem(false, item.getValue, writer, context)
      writer.endNode()
    }
  }

}

class JavaMapEntryConverter(mapper: Mapper) extends AbstractMapConverter[ju.Map.Entry[Object, Object]] {

  def marshal(source: ju.Map.Entry[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.startNode("entry", source.getClass())
    writeItem(true, source.getKey, writer, context)
    writeItem(false, source.getValue, writer, context)
    writer.endNode()
  }

}