package org.beangle.data.jdbc.script
import org.beangle.commons.lang.Strings._

class Parser {
  def parse(content: String): List[String] = {
    val lines = split(content, "\n")
    val buf = new collection.mutable.ListBuffer[String]
    val stateBuf = new collection.mutable.ListBuffer[String]
    var tails: Seq[String] = List.empty
    for (l <- lines; line = trim(l); if !isEmpty(l) && !isComment(line)) {
      if (tails.isEmpty) tails = endOf(l)
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

}

object OracleParser extends Parser {
  def main(args: Array[String]) {
    println("create or replace package advads".matches("create(.*?)package(.*?)"))
  }

  override def endOf(line: String): Seq[String] = {
    val lower = line.toLowerCase()
    if (lower.startsWith("prompt") || lower.startsWith("set")) List("", ";")
    else if (lower.matches("create(.*?)package(.*?)")) List("/")
    else if (lower.matches("create(.*?)type(.*?)")) List("/")
    else if (lower.matches("create(.*?)function(.*?)")) List("/")
    else if (lower.matches("create(.*?)procedure(.*?)")) List("/")
    else List(";")
  }
}