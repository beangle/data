package org.beangle.data.serializer.io.json

import org.beangle.data.serializer.io.AbstractWriter
import org.beangle.data.serializer.io.json.AbstractJsonWriter._
import java.io.Writer

class JsonWriter(writer: Writer, mode: Int, format: Format) extends AbstractJsonWriter(mode) {

  private var depth: Int = if ((mode & DROP_ROOT_MODE) == 0) -1 else 0
  private var newLineProposed: Boolean = _

  def this(writer: Writer) {
    this(writer, 0, new Format(Array(' ', ' '), Array('\n'), Format.SPACE_AFTER_LABEL | Format.COMPACT_EMPTY_ELEMENT))
  }

  override def flush(): Unit = {
    writer.flush()
  }

  override def close(): Unit = {
    writer.close()
  }

  protected override def startObject() {
    if (newLineProposed) writeNewLine()
    writer.write('{')
    startNewLine()
  }

  protected override def addLabel(name: String): Unit = {
    if (newLineProposed) writeNewLine()

    writer.write('"')
    writeText(name)
    writer.write("\":")
    if ((format.mode & Format.SPACE_AFTER_LABEL) != 0) {
      writer.write(' ')
    }
  }

  protected override def addValue(value: String, ty: Type) {
    if (newLineProposed)
      writeNewLine()
    if (ty == STRING) writer.write('"')
    writeText(value)
    if (ty == STRING) writer.write('"')
  }
  protected override def startArray(): Unit = {
    if (newLineProposed) writeNewLine()
    writer.write("[")
    startNewLine()
  }

  protected override def nextElement(): Unit = {
    writer.write(",")
    writeNewLine()
  }

  protected override def endArray(): Unit = {
    endNewLine()
    writer.write("]")
  }

  protected override def endObject(): Unit = {
    endNewLine()
    writer.write("}")
  }

  private def startNewLine(): Unit = {
    depth += 1
    if (depth > 0) newLineProposed = true
  }

  private def endNewLine(): Unit = {
    if (depth > 0) {
      if (((format.mode & Format.COMPACT_EMPTY_ELEMENT) != 0) && newLineProposed) {
        newLineProposed = false
      } else {
        writeNewLine()
      }
      depth -= 1
    }
  }

  private def writeNewLine(): Unit = {
    var depth = this.depth
    writer.write(format.newLine)
    while (depth > 0) {
      writer.write(format.lineIndenter)
      depth -= 1
    }
    newLineProposed = false
  }

  private def writeText(text: String) {
    val length = text.length()
    (0 until length) foreach { i =>
      val c = text.charAt(i)
      c match {
        case '"' => this.writer.write("\\\"")
        case '\\' => this.writer.write("\\\\")
        case '\b' => this.writer.write("\\b")
        case '\f' => this.writer.write("\\f")
        case '\n' => this.writer.write("\\n")
        case '\r' => this.writer.write("\\r")
        case '\t' => writer.write("\\t")
        case _ =>
          if (c > 0x1f) {
            this.writer.write(c)
          } else {
            this.writer.write("\\u")
            val hex = "000" + Integer.toHexString(c)
            this.writer.write(hex.substring(hex.length() - 4))
          }
      }
    }
  }

}

/**
 * Format  definition for JSON.
 */
object Format {
  val SPACE_AFTER_LABEL = 1
  val COMPACT_EMPTY_ELEMENT = 2
}

class Format(val lineIndenter: Array[Char], val newLine: Array[Char], val mode: Int) {
}
