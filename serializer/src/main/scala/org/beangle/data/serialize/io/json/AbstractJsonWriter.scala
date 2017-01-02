/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.serialize.io.json

import java.io.Writer
import org.beangle.data.serialize.marshal.MarshallerRegistry
import org.beangle.data.serialize.marshal.Type.{ Boolean, Number }
import org.beangle.data.serialize.io.AbstractWriter
import org.beangle.data.serialize.marshal.MarshallingContext

abstract class AbstractJsonWriter(val writer: Writer, val registry: MarshallerRegistry) extends AbstractWriter {

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
  
  override def start(context: MarshallingContext): Unit = {

  }

  override  def end(context: MarshallingContext): Unit = {

  }
}