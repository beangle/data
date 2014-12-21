package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter

object ObjectMarshaller extends Marshaller[Object] {
  override def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(source.toString)
  }

  override def support(clazz: Class[_]): Boolean = {
    !clazz.isArray
  }
}