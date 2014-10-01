package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.MarshallingContext

class TupleConvertor(mapper: Mapper) extends Converter[Product] {
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
      // todo: this is duplicated in TreeMarshaller.start
      val name = mapper.serializedClass(null);
      writer.startNode(name, classOf[Null])
      writer.addAttribute("index", String.valueOf(index))
    } else {
      val name = mapper.serializedClass(realitem.getClass)
      writer.startNode(name, realitem.getClass)
      writer.addAttribute("index", String.valueOf(index))
      context.convert(realitem, writer)
    }
    writer.endNode()
  }
}