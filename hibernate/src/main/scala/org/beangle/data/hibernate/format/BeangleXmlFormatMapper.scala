/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.hibernate.format

import org.beangle.commons.xml.{Document, Element}
import org.hibernate.`type`.descriptor.WrapperOptions
import org.hibernate.`type`.descriptor.java.JavaType
import org.hibernate.`type`.format.FormatMapper

/** Hibernate XML {@link org.hibernate.type.format.FormatMapper} backed by beangle-commons xml. */
class BeangleXmlFormatMapper extends FormatMapper {

  override def fromString[T](charSequence: CharSequence, javaType: JavaType[T], wrapperOptions: WrapperOptions): T = {
    val text = charSequence.toString
    javaType.getJavaType match {
      case t if t == classOf[String] => text.asInstanceOf[T]
      case t if t == classOf[Document] => Document.parse(text).asInstanceOf[T]
      case t if t == classOf[Element] => Document.parse(text).asInstanceOf[T]
      case other => throw new IllegalArgumentException(s"Unsupported XML type: $other")
    }
  }

  override def toString[T](value: T, javaType: JavaType[T], wrapperOptions: WrapperOptions): String = {
    value match {
      case s: String => s
      case e: Element => e.toXml
      case other => throw new IllegalArgumentException(s"Unsupported XML value: ${other.getClass}")
    }
  }
}
