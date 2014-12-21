package org.beangle.data.serialize.marshal

import java.{ util => ju }

import org.beangle.commons.collection.Properties
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.data.serialize.marshal.Type.Type

class PropertiesMarshaller(val mapper: Mapper) extends Marshaller[ju.Properties] {

  def marshal(source: ju.Properties, writer: StreamWriter, context: MarshallingContext): Unit = {
    val enum = source.propertyNames()
    while (enum.hasMoreElements) {
      val key = enum.nextElement.asInstanceOf[String]
      val value = source.getProperty(key)
      if (null != value) {
        writer.startNode(mapper.serializedMember(source.getClass(), key), value.getClass)
        context.marshal(value)
        writer.endNode()
      }
    }
  }

  override def targetType: Type = {
    Type.Object
  }

}

class JsonObjectMarshaller(val mapper: Mapper) extends Marshaller[Properties] {

  def marshal(source: Properties, writer: StreamWriter, context: MarshallingContext): Unit = {
    val enum = source.keys.iterator
    while (enum.hasNext) {
      val key = enum.next()
      val value = source(key).asInstanceOf[AnyRef]
      if (null != value) {
        writer.startNode(mapper.serializedMember(source.getClass(), key), value.getClass)
        context.marshal(value)
        writer.endNode()
      }
    }
  }

  override def targetType: Type = {
    Type.Object
  }

}