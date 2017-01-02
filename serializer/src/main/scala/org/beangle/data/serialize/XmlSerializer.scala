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
package org.beangle.data.serialize

import org.beangle.commons.activation.MimeTypes
import org.beangle.data.serialize.io.xml.{ DomDriver, XmlDriver }
import org.beangle.data.serialize.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serialize.marshal.{ DefaultMarshallerRegistry, MarshallerRegistry }

import javax.activation.MimeType

object XmlSerializer {
  def apply(): XmlSerializer = {
    val driver = new DomDriver
    val mapper = new DefaultMapper
    val registry = new DefaultMarshallerRegistry(mapper)
    driver.registry = registry
    new XmlSerializer(driver, mapper, registry)
  }
}

class XmlSerializer(val driver: XmlDriver, val mapper: Mapper, val registry: MarshallerRegistry)
  extends AbstractSerializer {

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationXml)
  }

}

class XmlXPathSerializer(val driver: XmlDriver, val mapper: Mapper, val registry: MarshallerRegistry, absolutePath: Boolean, singleNode: Boolean)
  extends ReferenceByXPathSerializer(absolutePath, singleNode) {

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationXml)
  }

}