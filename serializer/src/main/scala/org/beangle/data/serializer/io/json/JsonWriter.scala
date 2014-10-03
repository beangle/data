package org.beangle.data.serializer.io.json

import java.io.Writer
import scala.collection.mutable.Stack
import org.beangle.commons.collection.FastStack
import org.beangle.data.serializer.io.{ AbstractWriter, StreamException }
import org.beangle.data.serializer.converter.Type._
import org.beangle.data.serializer.converter.ConverterRegistry
import org.beangle.data.serializer.io.PathStack
import java.{ util => ju }
import java.{ lang => jl }
import java.io.Externalizable

//TODO QuickWriter FastStack
class JsonWriter(writer: Writer, registry: ConverterRegistry, lineIndenter: Array[Char], newLine: Array[Char]) extends AbstractWriter {

  def this(writer: Writer, registry: ConverterRegistry) {
    this(writer, registry, Array(' ', ' '), Array('\n'))
  }

  override def startNode(name: String, clazz: Class[_]): Unit = {
    val depath = pathStack.size
    var inArray = (pathStack.size > 0 && registry.lookup(this.pathStack.peek().clazz).targetType == Collection)
    pathStack.push(name, clazz)
    if (!pathStack.isFirstInLevel) {
      writer.write(',')
      writer.write(newLine)
    }
    indent(depath)
    if (!inArray) {
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

  override def setValue(text: String): Unit = {
    val targetType = registry.lookup(this.pathStack.peek().clazz).targetType
    writeText(text.toCharArray, (targetType != Boolean && targetType != Number))
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

  override def flush(): Unit = {
    writer.flush()
  }

  override def close(): Unit = {
    writer.close()
  }

  private def writerChar(c: Char): Unit = {
    if (c <= '\u001f' || c > '\ud7ff' && c < '\ue000' || c >= '\ufffe')
      throw new StreamException("Invalid character 0x" + Integer.toHexString(c) + " in XML 1.0 stream")
    if (Character.isDefined(c) && !Character.isISOControl(c)) {
      writer.write(c)
    } else {
      writer.write("&#x")
      writer.write(Integer.toHexString(c))
      writer.write(';')
    }
  }

  private def writeText(text: Array[Char], quoted: Boolean): Unit = {
    val length = text.length
    if (quoted) writer.write("\"")
    (0 until length) foreach { i =>
      val c = text(i)
      c match {
        case '"' => writer.write("\\\"")
        case '\\' => writer.write("\\\\")
        case '\b' => writer.write("\\b")
        case '\f' => writer.write("\\f")
        case '\n' => writer.write("\\n")
        case '\r' => writer.write("\\r")
        case '\t' => writer.write("\\t")
        case _ =>
          if (c > 0x1f) {
            writer.write(c)
          } else {
            writer.write("\\u")
            val hex = "000" + Integer.toHexString(c)
            writer.write(hex.substring(hex.length() - 4))
          }
      }
    }
    if (quoted) writer.write("\"")
  }

  protected def indentNewLine(depth: Int) {
    writer.write(newLine)
    (0 until depth) foreach (i => writer.write(lineIndenter))
  }
  protected def indent(depth: Int) {
    (0 until depth) foreach (i => writer.write(lineIndenter))
  }
}