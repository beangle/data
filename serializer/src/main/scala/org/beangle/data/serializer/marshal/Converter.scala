package org.beangle.data.serializer.marshal

import org.beangle.data.serializer.io.StreamWriter

trait Converter[T] {
  def marshal(source: T, writer: StreamWriter, context: MarshallingContext): Unit
  
  def isConverterToLiteral:Boolean=false
}