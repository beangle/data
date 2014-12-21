package org.beangle.data.serialize.io.csv

import java.io.{ OutputStream, OutputStreamWriter, Writer }

import org.beangle.commons.io.BufferedWriter
import org.beangle.data.serialize.io.{ AbstractDriver, StreamWriter }

class DefaultCsvDriver(encoding: String = "UTF-8") extends AbstractDriver(encoding) with CsvDriver {

  override def createWriter(out: OutputStream): StreamWriter = {
    new DefaultCsvWriter(new BufferedWriter(new OutputStreamWriter(out, encoding)))
  }

  def createWriter(out: Writer): StreamWriter = {
    new DefaultCsvWriter(out)
  }
}
