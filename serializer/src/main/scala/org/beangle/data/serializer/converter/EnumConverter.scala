package org.beangle.data.serializer.converter

import org.beangle.data.serializer.marshal.MarshallingContext
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.converter.Type.Type

class EnumConverter extends Converter[Enumeration#Value] {

  var ordinal = false
  
  def marshal(source: Enumeration#Value, writer: StreamWriter, context: MarshallingContext): Unit = {
    if (ordinal) writer.setValue(String.valueOf(source.id))
    else writer.setValue(source.toString)
  }

  override def targetType: Type = {
    Type.String
  }
}