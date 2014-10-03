package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext

import Type.Type
import java.{ lang => jl }

class BooleanConverter extends Converter[jl.Boolean] {

  override def marshal(source: jl.Boolean, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(source.toString)
  }
  override def targetType: Type = {
    Type.Boolean
  }
}