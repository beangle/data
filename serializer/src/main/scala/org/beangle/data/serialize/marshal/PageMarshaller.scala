package org.beangle.data.serialize.marshal

import org.beangle.commons.collection.page.Page
import org.beangle.commons.lang.Primitives
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.data.serialize.marshal.Type.Type

class PageMarshaller(val mapper: Mapper) extends Marshaller[Page[Object]] {

  def marshal(source: Page[Object], writer: StreamWriter, context: MarshallingContext): Unit = {
    val sourceType = source.getClass
    val properties = context.getProperties(sourceType)
    val getters = BeanManifest.get(sourceType).getters
    properties foreach { property =>
      val itemType = Primitives.wrap(getters(property).returnType)
      writer.startNode(mapper.serializedMember(source.getClass, property), itemType)
      property match {
        case "pageIndex" =>
          context.marshal(Integer.valueOf(source.pageIndex))
        case "pageSize" =>
          context.marshal(Integer.valueOf(source.pageSize))
        case "totalPages" =>
          context.marshal(Integer.valueOf(source.totalPages))
        case "totalItems" =>
          context.marshal(Integer.valueOf(source.totalItems))
        case "items" =>
          context.marshal(source.items)
        case other: String => context.marshal(getters(other).method.invoke(source))
      }
      writer.endNode()
    }
  }

  protected def writeItem(item: Object, writer: StreamWriter, context: MarshallingContext) {
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

  override def support(clazz: Class[_]): Boolean = {
    (classOf[Page[_]].isAssignableFrom(clazz))
  }

  override def targetType: Type = {
    Type.Object
  }
}