package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.marshal.Type.Type

class EnumMarshaller extends Marshaller[Enumeration#Value] {

  var ordinal = false
  
  def marshal(source: Enumeration#Value, writer: StreamWriter, context: MarshallingContext): Unit = {
    if (ordinal) writer.setValue(String.valueOf(source.id))
    else writer.setValue(source.toString)
  }

  override def targetType: Type = {
    Type.String
  }
}