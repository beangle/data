package org.beangle.data.serialize.io.jsonp

import java.io.Writer
import org.beangle.data.serialize.io.{ AbstractDriver, StreamWriter }
import org.beangle.commons.io.BufferedWriter
import org.beangle.data.serialize.io.StreamDriver

class DefaultJsonpDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) with StreamDriver {

  def createWriter(out: Writer): StreamWriter = {
    new DefaultJsonpWriter(new BufferedWriter(out), registry)
  }
}