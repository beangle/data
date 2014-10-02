package org.beangle.data.serializer.marshal

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.converter.Converter

trait Marshaller {

  def marshal(obj: Object, writer: StreamWriter)

  def convert(item: Object, writer: StreamWriter, converter: Converter[Object], context: MarshallingContext): Unit
}