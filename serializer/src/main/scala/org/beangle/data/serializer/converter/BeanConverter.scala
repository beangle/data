package org.beangle.data.serializer.converter

import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext
import Type.Type
import java.beans.Transient

class BeanConverter(val mapper: Mapper) extends Converter[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    BeanManifest.get(sourceType).getters foreach {
      case (name, getter) =>
        if (!getter.method.isAnnotationPresent(classOf[Transient])) {
          val value = extractOption(getter.method.invoke(source))
          if (null != value) {
            writer.startNode(mapper.serializedMember(source.getClass(), name), value.getClass)
            context.convert(value, writer)
            writer.endNode()
          }
        }
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    !(clazz.getName.startsWith("java.") || clazz.getName.startsWith("scala.") || clazz.isArray)
  }

  override def targetType: Type = {
    Type.Object
  }
}

