package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.io.StreamWriter

import Type.Type

trait Marshaller[T] {
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