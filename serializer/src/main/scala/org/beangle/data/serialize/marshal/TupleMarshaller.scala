package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper

import Type.Type

class TupleConvertor(mapper: Mapper) extends Marshaller[Product] {
  def marshal(source: Product, writer: StreamWriter, context: MarshallingContext): Unit = {
    val iter = source.productIterator
    var i = 0
    writer.addAttribute("class", "tuple");
    while (iter.hasNext) {
      val item = iter.next
      i = i + 1
      writeItem(item.asInstanceOf[AnyRef], writer, context, i)
    }
  }

  override def support(clazz: Class[_]): Boolean = {
    clazz.getSimpleName().startsWith("Tuple")
  }

  protected def writeItem(item: AnyRef, writer: StreamWriter, context: MarshallingContext, index: Int) {
    val realitem = extractOption(item)
    if (realitem == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
      writer.addAttribute("index", String.valueOf(index))
    } else {
      val name = mapper.serializedClass(realitem.getClass)
      writer.startNode(name, realitem.getClass)
      writer.addAttribute("index", String.valueOf(index))
      context.marshal(realitem)
    }
    writer.endNode()
  }

  override def targetType: Type = {
    Type.Collection
  }
}