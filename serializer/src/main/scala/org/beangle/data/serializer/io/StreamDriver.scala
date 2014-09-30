package org.beangle.data.serializer.io

import java.io.{ OutputStream, Writer }

trait StreamDriver {
  def createWriter(out: Writer): StreamWriter
  def createWriter(out: OutputStream): StreamWriter
}