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
    new XmlSerializer(driver, mapper, registry, true, true)
  }
}

class XmlSerializer(val driver: XmlDriver, val mapper: Mapper, val registry: MarshallerRegistry, absolutePath: Boolean, singleNode: Boolean)
  extends ReferenceByXPathSerializer(absolutePath, singleNode) {

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationXml)
  }

}