package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext
import Type.Type

import java.{ util => ju }

class ArrayConverter(val mapper: Mapper) extends Converter[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    source.asInstanceOf[Array[AnyRef]].foreach { item =>
      val realitem = extractOption(item)
      if (realitem == null) {
        writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
      } else {
        val name = mapper.serializedClass(realitem.getClass())
        writer.startNode(name, realitem.getClass())
        context.convert(realitem, writer)
      }
      writer.endNode()
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    clazz.isArray
  }

  override def targetType: Type = {
    Type.Collection
  }
}
