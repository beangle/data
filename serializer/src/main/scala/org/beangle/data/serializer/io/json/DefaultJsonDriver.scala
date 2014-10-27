package org.beangle.data.serializer.io.json

import java.io.Writer
import org.beangle.data.serializer.io.{ AbstractDriver, StreamWriter }
import org.beangle.commons.io.BufferedWriter

class DefaultJsonDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) with JsonDriver {
  var compressOutput = true

  def createWriter(out: Writer): StreamWriter = {
    if (compressOutput) new DefaultJsonWriter(new BufferedWriter(out), registry)
    else new PrettyJsonWriter(new BufferedWriter(out), registry)
  }
}