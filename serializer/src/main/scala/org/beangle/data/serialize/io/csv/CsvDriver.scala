package org.beangle.data.serialize.io.csv

import java.io.{ OutputStream, OutputStreamWriter, Writer }
import org.beangle.commons.io.BufferedWriter
import org.beangle.data.serialize.io.{ AbstractDriver, StreamWriter }
import org.beangle.data.serialize.io.Path
import org.beangle.data.serialize.io.PathStack
import org.beangle.data.serialize.io.AbstractWriter

class CsvDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) {

  override def createWriter(out: OutputStream): StreamWriter = {
    new DefaultCsvWriter(new BufferedWriter(new OutputStreamWriter(out, encoding)))
  }

  def createWriter(out: Writer): StreamWriter = {
    new DefaultCsvWriter(out)
  }
}
