package org.beangle.data.serializer.io.json

import java.io.Writer
import org.beangle.data.serializer.io.{ AbstractDriver, StreamWriter }
import org.beangle.commons.io.BufferedWriter

class JsonDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) {
  def createWriter(out: Writer): StreamWriter = {
    return new JsonWriter(new BufferedWriter(out), registry)
  }
}