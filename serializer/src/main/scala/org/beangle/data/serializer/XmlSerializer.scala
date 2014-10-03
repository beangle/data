package org.beangle.data.serializer

import org.beangle.commons.activation.MimeTypes
import org.beangle.commons.io.Serializer
import org.beangle.data.serializer.converter.{ ConverterRegistry, DefaultConverterRegistry }
import org.beangle.data.serializer.io.StreamDriver
import org.beangle.data.serializer.io.xml.DomDriver
import org.beangle.data.serializer.mapper.{ DefaultMapper, Mapper }
import AbstractSerializer.SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES
import javax.activation.MimeType
import org.beangle.data.serializer.io.xml.XmlDriver

object XmlSerializer {

  import AbstractSerializer._
  def apply(): XmlSerializer = {
    val driver = new DomDriver
    val registry = new DefaultConverterRegistry
    driver.registry = registry
    new XmlSerializer(driver, new DefaultMapper, registry).setMode(SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES)
  }
}

class XmlSerializer(driver: XmlDriver, mapper: Mapper, registry: ConverterRegistry)
  extends AbstractSerializer(driver, mapper, registry) with Serializer {

  import XmlSerializer._

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationXml)
  }

}