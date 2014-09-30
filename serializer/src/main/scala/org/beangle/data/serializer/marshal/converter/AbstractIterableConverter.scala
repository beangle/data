package org.beangle.data.serializer.marshal.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.{ Converter, MarshallingContext }

abstract class AbstractIterableConverter[T <: Iterable[_]](val mapper: Mapper) extends Converter[T] {

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    if (item == null) {
      // todo: this is duplicated in TreeMarshaller.start()
      val name = mapper.serializedClass(null);
      writer.startNode(name, classOf[Null])
    } else {
      val name = mapper.serializedClass(item.getClass());
      writer.startNode(name, item.getClass());
      context.convert(item, writer)
    }
    writer.endNode()
  }
}

abstract class AbstractCollectionConverter[T](val mapper: Mapper) extends Converter[T] {

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
    if (item == null) {
      // todo: this is duplicated in TreeMarshaller.start()
      val name = mapper.serializedClass(null)
      writer.startNode(name, classOf[Null])
    } else {
      val name = mapper.serializedClass(item.getClass());
      writer.startNode(name, item.getClass())
      context.convert(item, writer)
    }
    writer.endNode()
  }
}

