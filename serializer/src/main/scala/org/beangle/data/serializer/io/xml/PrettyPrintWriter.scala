package org.beangle.data.serializer.io.xml

import org.beangle.data.serializer.io.NameCoder
import java.io.Writer
import org.beangle.data.serializer.io.StreamWriter
import scala.collection.mutable.Stack
import org.beangle.data.serializer.io.StreamException
import org.beangle.data.serializer.io.AbstractWriter

object PrettyPrintWriter {
  val NULL = "&#x0;".toCharArray()
  val AMP = "&amp;".toCharArray()
  val LT = "&lt;".toCharArray()
  val GT = "&gt;".toCharArray()
  val CR = "&#xd;".toCharArray()
  val QUOT = "&quot;".toCharArray()
  val APOS = "&apos;".toCharArray()
  val CLOSE = "</".toCharArray()

}

//TODO QuickWriter FastStack
class PrettyPrintWriter(writer: Writer, lineIndenter: Array[Char], nameCoder: NameCoder, newLine: String) extends AbstractWriter(nameCoder) {

  import PrettyPrintWriter._

  def this(writer: Writer, nameCoder: NameCoder) {
    this(writer, Array(' ', ' '), nameCoder, "\n")
  }

  private var tagInProgress: Boolean = _
  protected var depth: Int = _
  private var readyForNewLine: Boolean = _
  private var tagIsEmpty: Boolean = _

  private final val elementStack = new Stack[String]

  override def startNode(name: String, clazz: Class[_]): Unit = {
    val escapedName = encodeNode(name)
    tagIsEmpty = false
    finishTag()
    writer.write('<')
    writer.write(escapedName)
    elementStack.push(escapedName)
    tagInProgress = true
    depth += 1
    readyForNewLine = true
    tagIsEmpty = true
  }

  override def addAttribute(key: String, value: String): Unit = {
    writer.write(' ')
    writer.write(encodeAttribute(key))
    writer.write('=')
    writer.write('\"')
    writeAttributeValue(writer, value)
    writer.write('\"')
  }

  override def setValue(text: String): Unit = {
    readyForNewLine = false
    tagIsEmpty = false
    finishTag()

    writeText(writer, text)
  }

  override def endNode(): Unit = {
    val name = elementStack.pop()
    depth -= 1
    if (tagIsEmpty) {
      writer.write('/')
      readyForNewLine = false
      finishTag()
    } else {
      finishTag()
      writer.write(CLOSE)
      writer.write(name)
      writer.write('>')
    }
    readyForNewLine = true
    if (depth == 0) {
      writer.flush()
    }
  }

  override def flush(): Unit = {
    writer.flush()
  }

  override def close(): Unit = {
    writer.close()
  }

  protected def writeAttributeValue(writer: Writer, text: String): Unit = {
    writeText(text, true)
  }

  protected def writeText(writer: Writer, text: String): Unit = {
    writeText(text, false)
  }
  private def writerChar(c: Char): Unit = {
    if (  c <= '\u001f' || c > '\ud7ff' && c < '\ue000' || c >= '\ufffe')
      throw new StreamException("Invalid character 0x" + Integer.toHexString(c) + " in XML 1.0 stream")
    if (Character.isDefined(c) && !Character.isISOControl(c)) {
      this.writer.write(c)
    } else {
      this.writer.write("&#x")
      this.writer.write(Integer.toHexString(c))
      this.writer.write(';')
    }
  }
  private def writeText(text: String, isAttribute: Boolean) {
    val length = text.length()
    (0 until length) foreach { i =>
      var c = text.charAt(i)
      c match {
        case '&' => this.writer.write(AMP)
        case '<' => this.writer.write(LT)
        case '>' => this.writer.write(GT)
        case '"' => this.writer.write(QUOT)
        case '\'' => this.writer.write(APOS)
        case '\r' => this.writer.write(CR)
        case '\t' | '\n' => if (!isAttribute) this.writer.write(c) else writerChar(c)
        case _ => writerChar(c)
      }
    }
  }

  private def finishTag() {
    if (tagInProgress) {
      writer.write('>')
      tagInProgress = false
    }
    if (readyForNewLine) {
      endOfLine()
      readyForNewLine = false
    }
    tagIsEmpty = false
  }
  protected def endOfLine() {
    writer.write(newLine)
    (0 until depth) foreach (i => writer.write(lineIndenter))
  }
}