package org.beangle.data.serialize.io

import java.io.{ OutputStream, Writer }
import org.beangle.data.serialize.marshal.MarshallerRegistry

trait StreamDriver {
  var registry: MarshallerRegistry = _
  def createWriter(out: Writer): StreamWriter
  def createWriter(out: OutputStream): StreamWriter
}