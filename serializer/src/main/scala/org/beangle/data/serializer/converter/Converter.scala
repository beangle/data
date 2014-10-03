package org.beangle.data.serializer.converter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext

import Type.Type

trait Converter[T] {
  def marshal(source: T, writer: StreamWriter, context: MarshallingContext): Unit

  def support(clazz: Class[_]): Boolean = {
    true
  }

  def targetType: Type = {
    Type.String
  }

  def extractOption(item: AnyRef): AnyRef = {
    if (item == null) return null
    else {
      if (item.isInstanceOf[Option[_]]) item.asInstanceOf[Option[AnyRef]].getOrElse(null)
      else item
    }
  }
}