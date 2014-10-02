package org.beangle.data.serializer.io.path

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.io.WriterWrapper
import org.beangle.data.serializer.io.AbstractWriter

class PathTrackingWriter(writer: StreamWriter, pathTracker: PathTracker) extends WriterWrapper(writer) {

  override def startNode(name: String, clazz: Class[_]): Unit = {
    pathTracker.pushElement(name)
    super.startNode(name, clazz)
  }

  override def endNode(): Unit = {
    super.endNode()
    pathTracker.popElement()
  }
}
