package org.beangle.data.jdbc.script

import javax.sql.DataSource
import org.beangle.commons.logging.Logging
import java.net.URL
import Scripts._
import org.beangle.commons.io.IOs
import org.beangle.commons.io.StringBuilderWriter
import java.io.InputStream
import java.io.InputStreamReader
import org.beangle.commons.lang.Charsets

object Scripts {
  def read(parser: Parser, urls: URL*): List[Script] = {
    val buf = new collection.mutable.ListBuffer[Script]
    for (url <- urls) {
      var in: InputStream = null
      try {
        in = url.openStream()
        val sw = new StringBuilderWriter(16)
        IOs.copy(new InputStreamReader(in, Charsets.UTF_8), sw)
        buf += new Script(url,parser.parse(sw.toString))
      } finally {
        IOs.close(in)
      }
    }
    buf.toList
  }
}
class Scripts(parser: Parser, urls: URL*) {
  val list = read(parser, urls: _*)
}
class Script(val source:Any,val statements: List[String]) extends Logging {

  def execute(dataSource: DataSource, ignoreError: Boolean) {
    val conn = dataSource.getConnection()
    var terminated = false
    val iter = statements.iterator
    while (!terminated && iter.hasNext) {
      val statement = iter.next()
      try {
        conn.createStatement().execute(statement)
      } catch {
        case e: Exception => {
          logger.error("Failure when exceute sql " + statement, e)
          if (!ignoreError) terminated = true;
        }
      }
    }
  }
}