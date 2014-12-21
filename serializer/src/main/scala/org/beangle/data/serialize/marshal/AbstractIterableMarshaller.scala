package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

import Type.Type

abstract class AbstractIterableMarshaller[T <: Iterable[_]](val mapper: Mapper) extends Marshaller[T] {

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      val name = mapper.serializedClass(realitem.getClass())
      writer.startNode(name, realitem.getClass())
      context.marshal(realitem)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}

abstract class AbstractCollectionMarshaller[T](val mapper: Mapper) extends Marshaller[T] {

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      val name = mapper.serializedClass(realitem.getClass())
      writer.startNode(name, realitem.getClass())
      context.marshal(realitem)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}
