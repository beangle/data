package org.beangle.data.serializer.marshal.impl

import org.beangle.data.serializer.converter.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.{ Marshaller, MarshallingContext }
import org.beangle.data.serializer.SerializeException

class TreeMarshaller(val registry: ConverterRegistry, val mapper: Mapper) extends Marshaller {

  override def convert(item: Object, writer: StreamWriter, converter: Converter[Object], context: MarshallingContext): Unit = {
    if (converter.targetType.scalar) {
      converter.marshal(item, writer, context)
    } else {
      val references = context.references
      if (references.contains(item)) {
        val e = new SerializeException("Recursive reference to parent object")
        e.add("item-type", item.getClass().getName())
        e.add("converter-type", converter.getClass().getName())
        throw e
      }
      references.put(item, null)
      converter.marshal(item, writer, context)
      references.remove(item)
    }
  }

  override def marshal(item: Object, writer: StreamWriter): Unit = {
    val context = createMarshallingContext(writer, registry)
    val newwriter = context.writer
    if (item == null) {
      newwriter.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      newwriter.startNode(mapper.serializedClass(item.getClass()), item.getClass())
      context.convert(item, newwriter, null)
    }
    newwriter.endNode()
  }

  protected def createMarshallingContext(writer: StreamWriter, registry: ConverterRegistry): MarshallingContext = {
    return new MarshallingContext(this, writer, registry)
  }
}

