package org.beangle.data.serializer.formatter

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext

trait Formatter[T] {
  def toString(source: T): String
}