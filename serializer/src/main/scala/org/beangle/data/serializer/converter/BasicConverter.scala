package org.beangle.data.serializer.converter

import org.beangle.data.serializer.marshal.MarshallingContext
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serializer.mapper.Mapper

class BasicConverter extends Converter[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(source.toString)
  }

  override def support(clazz: Class[_]): Boolean = {
    clazz.getName.startsWith("java.lang") ||
      clazz.getName.startsWith("java.math") ||
      clazz.getName.startsWith("scala.math")
  }
}