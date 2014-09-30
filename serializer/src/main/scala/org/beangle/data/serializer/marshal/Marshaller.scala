package org.beangle.data.serializer.marshal

import org.beangle.data.serializer.io.StreamWriter

trait Marshaller {

  def marshal(obj: Object, writer: StreamWriter, dataHolder: DataHolder)

  def convert(item: Object, writer: StreamWriter, converter: Converter[Object], context: MarshallingContext): Unit
}