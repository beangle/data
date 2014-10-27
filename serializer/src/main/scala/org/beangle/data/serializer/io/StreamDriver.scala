package org.beangle.data.serializer.io

import java.io.{ OutputStream, Writer }
import org.beangle.data.serializer.converter.ConverterRegistry

trait StreamDriver {
  var registry: ConverterRegistry = _
  def createWriter(out: Writer): StreamWriter
  def createWriter(out: OutputStream): StreamWriter
}