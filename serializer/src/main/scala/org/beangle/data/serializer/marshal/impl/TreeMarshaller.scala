package org.beangle.data.serializer.marshal.impl

import org.beangle.data.serializer.converter.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.{ CircularReferenceException, Marshaller, MarshallingContext }

class TreeMarshaller(val registry: ConverterRegistry, val mapper: Mapper) extends Marshaller {

  override def convert(item: Object, writer: StreamWriter, converter: Converter[Object], context: MarshallingContext): Unit = {
    val parentObjects = context.parentObjects
    if (parentObjects.containsId(item)) {
      val e = new CircularReferenceException("Recursive reference to parent object")
      e.add("item-type", item.getClass().getName())
      e.add("converter-type", converter.getClass().getName())
      throw e
    }
    parentObjects.associateId(item, "")
    converter.marshal(item, writer, context)
    parentObjects.removeId(item)
  }

  override def marshal(item: Object, writer: StreamWriter): Unit = {
    val context = createMarshallingContext(writer, registry)
    if (item == null) {
      writer.startNode(mapper.serializedClass(null), null)
    } else {
      writer.startNode(mapper.serializedClass(item.getClass()), item.getClass())
      context.convert(item, writer, null)
    }
    writer.endNode()
  }

  protected def createMarshallingContext(writer: StreamWriter, registry: ConverterRegistry): MarshallingContext = {
    return new MarshallingContext(this, writer, registry)
  }
}

