/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.script

import java.io.{ InputStream, InputStreamReader }
import java.net.{ URL }

import org.beangle.commons.io.{ IOs, StringBuilderWriter }
import org.beangle.commons.lang.Charsets
import org.beangle.commons.lang.Strings.{ substringBefore, lowerCase, substringAfter, trim }
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import Runner._
import javax.sql.DataSource

object Runner {
  def read(parser: Parser, urls: URL*): List[Script] = {
    val buf = new collection.mutable.ListBuffer[Script]
    for (url <- urls) {
      var in: InputStream = null
      try {
        in = url.openStream()
        val sw = new StringBuilderWriter(16)
        IOs.copy(new InputStreamReader(in, Charsets.UTF_8), sw)
        buf += new Script(url, parser.parse(sw.toString))
      } finally {
        IOs.close(in)
      }
    }
    buf.toList
  }
}

class Runner(parser: Parser, urls: URL*) extends Logging {
  val list = read(parser, urls: _*)

  def execute(dataSource: DataSource, ignoreError: Boolean) {
    val watch = new Stopwatch(true)

    for (script <- list) {
      val sw = new Stopwatch(true)
      val conn = dataSource.getConnection()
      conn.setAutoCommit(true)
      val stm = conn.createStatement()
      var terminated = false
      val iter = script.statements.iterator
      val commands = parser.commands
      while (!terminated && iter.hasNext) {
        val statement = iter.next()
        val cmd = lowerCase(substringBefore(statement, " "))
        if (commands.contains(cmd)) {
          if (cmd == "prompt") logger.info(trim(substringAfter(statement, cmd)))
          else logger.info(statement)
        } else {
          try {
            stm.execute(statement)
          } catch {
            case e: Exception => {
              logger.error(s"Failure when exceute sql $statement", e)
              if (!ignoreError) terminated = true;
            }
          }
        }
      }
      stm.close()
      conn.commit()
      conn.close()
      logger.info(s"exec ${script.source} using $sw")
    }
    logger.info(s"exec sql using $watch")
  }
}

class Script(val source: Any, val statements: List[String]) {

}
