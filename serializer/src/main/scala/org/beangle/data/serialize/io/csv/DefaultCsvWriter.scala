/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.serialize.io.csv

import java.io.Writer
import org.beangle.data.serialize.io.{ AbstractWriter, StreamWriter }
import org.beangle.data.serialize.marshal.MarshallingContext
import scala.collection.mutable.ListBuffer
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.marshal.Type

class DefaultCsvWriter(out: Writer) extends AbstractWriter {
  val innerWriter = new org.beangle.commons.csv.CsvWriter(out)

  val buf = new collection.mutable.ListBuffer[String]

  def startNode(name: String, clazz: Class[_]): Unit = {
    pathStack.push(name, clazz)
  }

  def addAttribute(key: String, value: String): Unit = {
    buf += value
  }

  def setValue(text: String): Unit = {
    buf += text
  }

  def endNode(): Unit = {
    pathStack.pop()
    if (pathStack.size == 1) {
      innerWriter.write(buf.toArray)
      buf.clear()
    }
  }
  def write(nextLine: Array[String]) {
    innerWriter.write(nextLine)
  }

  def flush(): Unit = {
    out.flush()
  }

  def close(): Unit = {
    out.close()
  }

  override def start(context: MarshallingContext): Unit = {
    val propertyNames = getProperties(context)
    if (propertyNames.length > 0)
      innerWriter.write(propertyNames)
  }

  def getProperties(context: MarshallingContext): Array[String] = {
    val propertyNames = new ListBuffer[String]
    if (null != context.elementType) {
      val manifest = BeanManifest.get(context.elementType)
      val processed = new collection.mutable.HashSet[Class[_]]
      processed += context.elementType
      for (name <- context.getProperties(context.elementType)) {
        addAttribute("", name, manifest.getPropertyType(name).get, propertyNames, context, processed)
      }
    }
    propertyNames.toArray
  }

  private def addAttribute(prefix: String, name: String, clazz: Class[_], names: ListBuffer[String],
    context: MarshallingContext, processed: collection.mutable.HashSet[Class[_]]): Unit = {
    if (processed.contains(clazz)) {
      names += (prefix + name)
      return
    }
    val properties = context.getProperties(clazz)
    if (properties.isEmpty) {
      names += (prefix + name)
    } else {
      val manifest = BeanManifest.get(clazz)
      processed += clazz
      properties foreach { n =>
        addAttribute(prefix + name + ".", n, manifest.getPropertyType(n).get, names, context, processed)
      }
    }
  }
  override def end(context: MarshallingContext): Unit = {

  }
}