package org.beangle.data.serializer.io.xml

import org.beangle.data.serializer.io.{ AbstractDriver, NameCoder, NoNameCoder }
import javax.xml.parsers.DocumentBuilderFactory
import java.io.UnsupportedEncodingException
import org.beangle.data.serializer.io.StreamWriter
import java.io.Writer
import java.io.OutputStream
import java.io.OutputStreamWriter

class DomDriver(encoding: String = "UTF-8", nameCoder: NameCoder = NoNameCoder) extends AbstractDriver(encoding, nameCoder) {

  private final val documentBuilderFactory = DocumentBuilderFactory.newInstance()

  def createWriter(out: Writer): StreamWriter = {
    return new PrettyPrintWriter(out, nameCoder)
  }
}