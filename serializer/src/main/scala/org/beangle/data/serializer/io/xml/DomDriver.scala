package org.beangle.data.serializer.io.xml

import java.io.Writer

import org.beangle.commons.io.BufferedWriter
import org.beangle.data.serializer.io.{ AbstractDriver, StreamWriter }

class DomDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) with XmlDriver {

  def createWriter(out: Writer): StreamWriter = {
    return new PrettyPrintWriter(new BufferedWriter(out))
  }
}