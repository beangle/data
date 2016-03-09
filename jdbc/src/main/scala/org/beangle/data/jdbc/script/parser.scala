/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.script
import org.beangle.commons.lang.Strings._
import org.beangle.commons.io.Files
import java.io.File

class Parser {
  def parse(content: String): List[String] = {
    val lines = split(content, "\n")
    val buf = new collection.mutable.ListBuffer[String]
    val stateBuf = new collection.mutable.ListBuffer[String]
    var tails: Seq[String] = List.empty
    for (l <- lines; line = trim(l); if isNotBlank(l) && !isComment(line)) {
      if (tails.isEmpty) tails = endOf(line)
      if (!stateBuf.isEmpty) stateBuf += "\n"
      stateBuf += line
      val iter = tails.iterator
      while (!tails.isEmpty && iter.hasNext) {
        val tail = iter.next()
        if (line.endsWith(tail)) {
          if (tail.length > 0) buf += substringBeforeLast(stateBuf.mkString, tail)
          else buf += stateBuf.mkString
          stateBuf.clear()
          tails = List.empty
        }
      }
    }
    buf.toList
  }

  def isComment(line: String): Boolean = line.startsWith("--")

  def endOf(line: String): Seq[String] = List(";")

  def commands: Set[String] = Set.empty
}

object OracleParser extends Parser {

  override def commands = Set("set", "prompt", "exit")

  override def endOf(line: String): Seq[String] = {
    val lower = line.toLowerCase()
    val cmd = substringBefore(lower, " ")
    if (commands.contains(cmd)) List("", ";")
    else if (lower.matches("create(.*?) package (.*?)")) List("/")
    else if (lower.matches("create(.*?) type (.*?)")) List("/")
    else if (lower.matches("create(.*?) function (.*?)")) List("/")
    else if (lower.matches("create(.*?) procedure (.*?)")) List("/")
    else List(";")
  }
}