package org.beangle.data.serialize.marshal

import java.{ lang => jl }

import org.beangle.data.serialize.io.StreamWriter

import Type.Type

class BooleanMarshaller extends Marshaller[jl.Boolean] {

  override def marshal(source: jl.Boolean, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(source.toString)
  }
  override def targetType: Type = {
    Type.Boolean
  }
}