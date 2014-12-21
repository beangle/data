package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import Type.Type

import java.{ util => ju }

class ArrayMarshaller(val mapper: Mapper) extends Marshaller[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    source.asInstanceOf[Array[AnyRef]].foreach { item =>
      val realitem = extractOption(item)
      if (realitem == null) {
        writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
      } else {
        val name = mapper.serializedClass(realitem.getClass())
        writer.startNode(name, realitem.getClass())
        context.marshal(realitem)
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
