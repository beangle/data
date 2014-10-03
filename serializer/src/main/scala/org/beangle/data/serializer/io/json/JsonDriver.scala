package org.beangle.data.serializer.io.json

import java.io.Writer

import org.beangle.data.serializer.io.{AbstractDriver, StreamWriter}

class JsonDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) {
  def createWriter(out: Writer): StreamWriter = {
    return new JsonWriter(out,registry)
  }
}