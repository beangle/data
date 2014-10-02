package org.beangle.data.serializer.io

abstract class AbstractWriter extends StreamWriter {
  override def underlying: StreamWriter = {
    this
  }
}