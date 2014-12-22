package org.beangle.data.serialize.marshal

import org.beangle.commons.collection.page.Page
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.data.serialize.marshal.Type.Type

class PageMarshaller(val mapper: Mapper) extends Marshaller[Page[_]] {

  def marshal(source: Page[_], writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    val properties = List("pageNo", "pageSize", "totalPages", "totalItems", "items")
    val getters = BeanManifest.get(sourceType).getters
    properties foreach { name =>
      val getter = getters(name)
      if (!getter.isTransient) {
        val value = extractOption(getter.method.invoke(source))
        if (null != value) {
          writer.startNode(mapper.serializedMember(source.getClass, name), value.getClass)
          context.marshal(value)
          writer.endNode()
        } else {
          context.marshalNull(source, name)
        }
      }
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    (classOf[Page[_]].isAssignableFrom(clazz))
  }

  override def targetType: Type = {
    Type.Object
  }
}