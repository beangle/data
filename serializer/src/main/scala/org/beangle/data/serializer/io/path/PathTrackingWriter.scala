package org.beangle.data.serializer.io.path

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.io.WriterWrapper
import org.beangle.data.serializer.io.AbstractWriter

class PathTrackingWriter(writer: StreamWriter, pathTracker: PathTracker) extends WriterWrapper(writer) {

  private val nameEncoderWriter = writer.underlying match {
    case aw: AbstractWriter => aw
    case _ => null
  }

  override def startNode(name: String, clazz: Class[_]): Unit = {
    pathTracker.pushElement(if (null == nameEncoderWriter) name else nameEncoderWriter.encodeNode(name));
    super.startNode(name, clazz)
  }

  override def endNode(): Unit = {
    super.endNode()
    pathTracker.popElement()
  }
}
