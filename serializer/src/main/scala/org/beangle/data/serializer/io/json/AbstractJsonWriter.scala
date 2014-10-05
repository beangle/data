package org.beangle.data.serializer.io.json

import java.io.Writer

import org.beangle.data.serializer.converter.ConverterRegistry
import org.beangle.data.serializer.converter.Type.{ Boolean, Number }
import org.beangle.data.serializer.io.AbstractWriter

abstract class AbstractJsonWriter(val writer: Writer, val registry: ConverterRegistry) extends AbstractWriter {

  override def setValue(text: String): Unit = {
    val targetType = registry.lookup(this.pathStack.peek().clazz).targetType
    writeText(text.toCharArray, (targetType != Boolean && targetType != Number))
  }

  protected def writeText(text: Array[Char], quoted: Boolean): Unit = {
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

  final override def flush(): Unit = {
    writer.flush()
  }

  final override def close(): Unit = {
    writer.close()
  }

}