package org.beangle.data.serialize.marshal

import java.beans.Transient

import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

import Type.Type

class BeanMarshaller(val mapper: Mapper) extends Marshaller[Object] {

  def marshal(source: Object, writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    val properties = context.getProperties(sourceType)
    if (!properties.isEmpty) {
      val getters = BeanManifest.get(sourceType).getters
      properties foreach { name =>
        val getter = getters(name)
        if (!getter.isTransient) {
          val value = extractOption(getter.method.invoke(source))
          if (null != value) {
            writer.startNode(mapper.serializedMember(source.getClass, name), value.getClass)
            context.marshal(value)
            writer.endNode()
          }else{
            context.marshalNull(source,name)
          }
        }
      }
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    !(clazz.getName.startsWith("java.") || clazz.getName.startsWith("scala.") ||
      clazz.isArray || classOf[Seq[_]].isAssignableFrom(clazz) ||
      classOf[collection.Map[_, _]].isAssignableFrom(clazz))
  }

  override def targetType: Type = {
    Type.Object
  }
}

