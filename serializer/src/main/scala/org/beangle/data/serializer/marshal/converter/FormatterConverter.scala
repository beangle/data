package org.beangle.data.serializer.marshal.converter

import org.beangle.data.serializer.formatter.Formatter
import org.beangle.data.serializer.marshal.Converter
import org.beangle.data.serializer.marshal.MarshallingContext
import org.beangle.data.serializer.io.StreamWriter

class FormatterConverter[T](val formatter: Formatter[T]) extends Converter[T] {
  def marshal(source: T, writer: StreamWriter, context: MarshallingContext): Unit = {
    writer.setValue(formatter.toString(source))
  }
}