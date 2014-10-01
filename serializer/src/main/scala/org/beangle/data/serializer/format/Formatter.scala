package org.beangle.data.serializer.format

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.marshal.MarshallingContext

trait Formatter[T] {
  def toString(source: T): String
}