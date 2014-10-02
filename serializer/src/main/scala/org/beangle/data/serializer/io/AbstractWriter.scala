package org.beangle.data.serializer.io

import org.beangle.commons.collection.FastStack

abstract class AbstractWriter extends StreamWriter {

  var pathStack: PathStack = _

  def currentPath: Path = {
    pathStack.currentPath
  }

  override def underlying: StreamWriter = {
    this
  }
}
