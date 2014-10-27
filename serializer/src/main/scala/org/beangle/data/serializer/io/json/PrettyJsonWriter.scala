package org.beangle.data.serializer.io.json

import java.io.Writer

import org.beangle.data.serializer.converter.ConverterRegistry
import org.beangle.data.serializer.converter.Type.{Collection, Object}

class PrettyJsonWriter(writer: Writer, registry: ConverterRegistry, lineIndenter: Array[Char], newLine: Array[Char])
  extends AbstractJsonWriter(writer, registry) {

  def this(writer: Writer, registry: ConverterRegistry) {
    this(writer, registry, Array(' ', ' '), Array('\n'))
  }

  override def startNode(name: String, clazz: Class[_]): Unit = {
    val depth = pathStack.size
    var inArray = (depth > 0 && registry.lookup(this.pathStack.peek().clazz).targetType == Collection)
    pathStack.push(name, clazz)
    if (!pathStack.isFirstInLevel) {
      writer.write(',')
      writer.write(newLine)
    }
    indent(depth)
    if (!inArray && depth > 0) {
      writer.write("\"")
      writer.write(name)
      writer.write("\":")
    }
    registry.lookup(clazz).targetType match {
      case Collection =>
        writer.write('['); writer.write(newLine)
      case Object =>
        writer.write('{'); writer.write(newLine)
      case _ =>
    }
  }

  override def addAttribute(key: String, value: String): Unit = {
    indent(pathStack.size)
    writer.write(" \"@")
    writer.write(key)
    writer.write("\":")
    writeText(value.toCharArray(), true)
  }

  override def endNode(): Unit = {
    val clazz = pathStack.pop().clazz
    val depth = pathStack.size

    registry.lookup(clazz).targetType match {
      case Collection =>
        indentNewLine(depth); writer.write(']')
      case Object =>
        indentNewLine(depth); writer.write('}')
      case _ =>
    }
    if (pathStack.size == 0) writer.flush()
  }

  private def indentNewLine(depth: Int) {
    writer.write(newLine)
    (0 until depth) foreach (i => writer.write(lineIndenter))
  }
  private def indent(depth: Int) {
    (0 until depth) foreach (i => writer.write(lineIndenter))
  }
}