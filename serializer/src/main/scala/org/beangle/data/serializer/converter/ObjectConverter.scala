package org.beangle.data.serializer.converter

import org.beangle.data.serializer.marshal.MarshallingContext
import org.beangle.data.serializer.io.StreamWriter

object ObjectConverter extends Converter[Object] {
  override def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(source.toString)
  }

  override def isConverterToLiteral: Boolean = true
}