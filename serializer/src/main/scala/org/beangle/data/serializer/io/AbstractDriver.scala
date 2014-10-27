package org.beangle.data.serializer.io

import java.io.OutputStreamWriter
import java.io.OutputStream
import org.beangle.commons.io.BufferedWriter

abstract class AbstractDriver(val encoding: String) extends StreamDriver {

  override def createWriter(out: OutputStream): StreamWriter = {
    createWriter(new BufferedWriter(new OutputStreamWriter(out, encoding)))
  }
}