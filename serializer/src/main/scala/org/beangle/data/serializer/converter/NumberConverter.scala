package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext
import Type.Type
class NumberConverter extends Converter[Number] {

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