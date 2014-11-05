package org.beangle.data.serializer.converter

import java.{ util => ju }
import org.beangle.data.serializer.converter.Type.Type
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext
import org.beangle.commons.collection.Properties

class PropertiesConverter(val mapper: Mapper) extends Converter[ju.Properties] {

  def marshal(source: ju.Properties, writer: StreamWriter, context: MarshallingContext): Unit = {
    val enum = source.propertyNames()
    while (enum.hasMoreElements) {
      val key = enum.nextElement.asInstanceOf[String]
      val value = source.getProperty(key)
      if (null != value) {
        writer.startNode(mapper.serializedMember(source.getClass(), key), value.getClass)
        context.convert(value, writer)
        writer.endNode()
      }
    }
  }

  override def targetType: Type = {
    Type.Object
  }

}

class JsonObjectConverter(val mapper: Mapper) extends Converter[Properties] {

  def marshal(source: Properties, writer: StreamWriter, context: MarshallingContext): Unit = {
    val enum = source.keys.iterator
    while (enum.hasNext) {
      val key = enum.next()
      val value = source(key).asInstanceOf[AnyRef]
      if (null != value) {
        writer.startNode(mapper.serializedMember(source.getClass(), key), value.getClass)
        context.convert(value, writer)
        writer.endNode()
      }
    }
  }

  override def targetType: Type = {
    Type.Object
  }

}