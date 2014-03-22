package org.beangle.data.jdbc.vendor

import java.util.regex.Pattern
import org.beangle.commons.lang.Strings.replace

object Driver {
  def apply(className: String, prefix: String, formats: String*): DriverInfo = {
    new DriverInfo(className, prefix, formats)
  }
}

class DriverInfo(val className: String, val prefix: String, val urlformats: Seq[String]) {
}

class UrlFormat(val format: String) {
  val params: List[String] = findParams(format)

  private def findParams(format: String): List[String] = {
    val m = Pattern.compile("(<.*?>)").matcher(format)
    val ps = new collection.mutable.ListBuffer[String]
    while (m.find()) {
      val matched = m.group(0)
      ps += matched.substring(1, matched.length - 1)
    }
    ps.toList
  }

  def fill(values: Map[String, String]): String = {
    var result = format
    for ((k, v) <- values) result = replace(result, "<" + k + ">", v)
    result = replace(result, "[", "")
    result = replace(result, "]", "")
    result
  }
}