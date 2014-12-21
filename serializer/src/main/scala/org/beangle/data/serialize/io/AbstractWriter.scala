package org.beangle.data.serialize.io

import org.beangle.commons.collection.FastStack

abstract class AbstractWriter extends StreamWriter {

  protected var pathStack = new PathStack

  def currentPath: Path = {
    pathStack.currentPath
  }

  override def underlying: StreamWriter = {
    this
  }
}
