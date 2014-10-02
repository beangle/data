package org.beangle.data.serializer.io

import java.io.OutputStreamWriter
import java.io.OutputStream

abstract class AbstractDriver(val encoding: String) extends StreamDriver {

  override def createWriter(out: OutputStream): StreamWriter = {
    createWriter(new OutputStreamWriter(out, encoding))
  }
}