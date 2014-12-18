package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter

import Type.Type

class NumberMarshaller extends Marshaller[Number] {

  def marshal(source: Number, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(source.toString)
  }

  override def support(clazz: Class[_]): Boolean = {
    clazz.getName.startsWith("java.lang") ||
      clazz.getName.startsWith("java.math") ||
      clazz.getName.startsWith("scala.math")
  }

  override def targetType: Type = {
    Type.Number
  }
}