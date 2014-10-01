package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext

trait Converter[T] {
  def marshal(source: T, writer: StreamWriter, context: MarshallingContext): Unit

  def support(clazz: Class[_]): Boolean = {
    true
  }

  def isConverterToLiteral: Boolean = false

  def extractOption(item: AnyRef): AnyRef = {
    if (item == null) return null
    else {
      if (item.isInstanceOf[Option[_]]) item.asInstanceOf[Option[AnyRef]].getOrElse(null)
      else item
    }
  }
}