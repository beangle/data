package org.beangle.data.serializer.io

trait StreamWriter {

  def startNode(name: String,clazz: Class[_])

  def addAttribute(key: String, value: String)

  def setValue(text: String)

  def endNode()

  def flush()

  def close()

  def underlying: StreamWriter
}

trait DocumentWriter extends StreamWriter {
  def topLevelNodes(): List[AnyRef]
}