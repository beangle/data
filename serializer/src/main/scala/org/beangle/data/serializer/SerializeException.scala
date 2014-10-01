package org.beangle.data.serializer

import java.util.TreeMap

object SerializeException {
  final val SEPARATOR = "\n-------------------------------";
}
class SerializeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause) {
  private val stuff = new TreeMap[String, String]();

  def add(name: String, information: String) {
    var key = name;
    var i = 0;
    while (stuff.containsKey(key)) {
      val value = stuff.get(key);
      if (information.equals(value))
        return ;
      i = i + 1
      key = name + "[" + i + "]";
    }
    stuff.put(key, information);
  }

}