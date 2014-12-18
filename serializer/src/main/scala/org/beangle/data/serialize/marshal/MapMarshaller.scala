package org.beangle.data.serialize.marshal

import java.{ util => ju }

import org.beangle.commons.collection.Properties
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

import Type.Type

abstract class AbstractMapMarshaller[T] extends Marshaller[T] {
  protected def writeItem(key: Boolean, item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      val name = if (key) "key" else "value"
      writer.startNode(name, classOf[Null])
    } else {
      val name = if (key) "key" else "value"
      writer.startNode(name, realitem.getClass())
      context.marshal(realitem)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}

class MapMarshaller(mapper: Mapper) extends AbstractMapMarshaller[collection.Map[Object, Object]] {

  def marshal(source: collection.Map[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val entryName = "entry"
    source.foreach { item =>
      writer.startNode(entryName, item.getClass())
      writeItem(true, item._1, writer, context)
      writeItem(false, item._2, writer, context)
      writer.endNode();
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    clazz != classOf[Properties]
  }

}

class JavaMapMarshaller(mapper: Mapper) extends AbstractMapMarshaller[ju.Map[Object, Object]] {

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

class JavaMapEntryMarshaller(mapper: Mapper) extends AbstractMapMarshaller[ju.Map.Entry[Object, Object]] {

  def marshal(source: ju.Map.Entry[Object, Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.startNode("entry", source.getClass())
    writeItem(true, source.getKey, writer, context)
    writeItem(false, source.getValue, writer, context)
    writer.endNode()
  }

}