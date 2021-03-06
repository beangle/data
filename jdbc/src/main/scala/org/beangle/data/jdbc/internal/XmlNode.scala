/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.internal

import org.beangle.commons.collection.Collections

import scala.collection.mutable
import scala.xml.Utility.escape

object XmlNode {
  def apply(name: String, attrs: (String, String)*): XmlNode = {
    val node = new XmlNode(name)
    attrs foreach { case (k, v) =>
      node.attr(k, v)
    }
    node
  }
}

class XmlNode(val name: String) {
  private val attributes = Collections.newBuffer[(String, String)]
  private val children = Collections.newBuffer[XmlNode]

  def attr(key: String, value: String): this.type = {
    attributes += (key -> escape(value))
    this
  }

  def attr(key: String, value: Option[String]): this.type = {
    value foreach (attr(key, _))
    this
  }

  def createChild(name: String, attrs: (String, String)*): XmlNode = {
    val node = new XmlNode(name)
    attrs foreach { case (k, v) =>
      node.attr(k, v)
    }
    children += node
    node
  }

  def toXml: String = {
    val buf = new StringBuilder("""<?xml version="1.0"?>""")
    buf.append("\n")
    appendXml(this, buf)
    buf.toString
  }

  private def appendXml(node: XmlNode, buf: mutable.StringBuilder): Unit = {
    buf ++= s"<${node.name}"
    node.attributes foreach { case (k, v) =>
      buf ++= s""" $k="$v""""
    }
    if (node.children.isEmpty) {
      buf ++= "/>\n"
    } else {
      buf ++= ">\n"
      node.children foreach (appendXml(_, buf))
      buf ++= s"</${node.name}>\n"
    }
  }
}
