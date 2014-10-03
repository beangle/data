package org.beangle.data.serializer

import org.beangle.commons.activation.MimeTypes
import org.beangle.data.serializer.converter.{ ConverterRegistry, DefaultConverterRegistry }
import org.beangle.data.serializer.io.StreamDriver
import org.beangle.data.serializer.io.json.JsonDriver
import org.beangle.data.serializer.mapper.{ DefaultMapper, Mapper }

import AbstractSerializer.SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES
import javax.activation.MimeType

object JsonSerializer {
  import AbstractSerializer._
  def apply(): XmlSerializer = {
    val driver = new JsonDriver
    val registry = new DefaultConverterRegistry
    driver.registry = registry
    new XmlSerializer(driver, new DefaultMapper, registry).setMode(SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES)
  }
}

class JsonSerializer(streamDriver: StreamDriver, mapper: Mapper, registry: ConverterRegistry)
  extends AbstractSerializer(streamDriver, mapper, registry) {

  override def supportedMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationJson)
  }

}