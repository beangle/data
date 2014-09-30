package org.beangle.data.serializer.io

abstract class AbstractWriter(val nameCoder: NameCoder) extends StreamWriter {

  def encodeNode(name: String): String = {
    nameCoder.encodeNode(name)
  }

  def encodeAttribute(name: String): String = {
    nameCoder.encodeAttribute(name)
  }

  override def underlying: StreamWriter = {
    this
  }
}