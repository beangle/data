package org.beangle.data.serialize.io

import org.beangle.data.serialize.marshal.MarshallingContext

trait StreamWriter {

  def currentPath: Path

  def startNode(name: String, clazz: Class[_]): Unit

  def addAttribute(key: String, value: String): Unit

  def setValue(text: String): Unit

  def endNode(): Unit

  def flush(): Unit

  def close(): Unit

  def underlying: StreamWriter

  def start(context: MarshallingContext): Unit

  def end(context: MarshallingContext): Unit
}
