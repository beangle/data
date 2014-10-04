package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

import Type.Type

abstract class AbstractIterableConverter[T <: Iterable[_]](val mapper: Mapper) extends Converter[T] {

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      val name = mapper.serializedClass(realitem.getClass())
      writer.startNode(name, realitem.getClass())
      context.convert(realitem, writer)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}

abstract class AbstractCollectionConverter[T](val mapper: Mapper) extends Converter[T] {

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      val name = mapper.serializedClass(realitem.getClass())
      writer.startNode(name, realitem.getClass())
      context.convert(realitem, writer)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}
