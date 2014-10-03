package org.beangle.data.serializer.converter

import java.{ util => ju }

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

import Type.Type

class JavaMapConverter(mapper: Mapper) extends AbstractCollectionConverter[ju.Map[Object, Object]](mapper) {

  def marshal(source: ju.Map[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val entryName = mapper.serializedClass(classOf[ju.Map.Entry[_, _]])
    val iterator = source.entrySet().iterator()
    while (iterator.hasNext) {
      val item = iterator.next()
      writer.startNode(entryName, item.getClass())
      writeItem(item.getKey, writer, context)
      writeItem(item.getValue, writer, context)
      writer.endNode()
    }
  }

  override def targetType: Type = {
    Type.Collection
  }

}