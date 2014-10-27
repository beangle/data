package org.beangle.data.serializer.io.json

import java.io.Writer

import org.beangle.data.serializer.converter.ConverterRegistry
import org.beangle.data.serializer.converter.Type.{ Collection, Object }

class DefaultJsonWriter(writer: Writer, registry: ConverterRegistry) extends AbstractJsonWriter(writer, registry) {

  override def startNode(name: String, clazz: Class[_]): Unit = {
    val depth = pathStack.size
    var inArray = (depth > 0 && registry.lookup(this.pathStack.peek().clazz).targetType == Collection)
    pathStack.push(name, clazz)
    if (!pathStack.isFirstInLevel) {
      writer.write(',')
    }
    if (!inArray && depth > 0) {
      writer.write("\"")
      writer.write(name)
      writer.write("\":")
    }
    registry.lookup(clazz).targetType match {
      case Collection => writer.write('[')
      case Object => writer.write('{')
      case _ =>
    }
  }

  override def addAttribute(key: String, value: String): Unit = {
    writer.write(" \"")
    writer.write(key)
    writer.write("\":")
    writeText(value.toCharArray(), true)
  }

  override def endNode(): Unit = {
    val clazz = pathStack.pop().clazz
    val depth = pathStack.size

    registry.lookup(clazz).targetType match {
      case Collection => writer.write(']')
      case Object => writer.write('}')
      case _ =>
    }
    if (pathStack.size == 0) writer.flush()
  }

}