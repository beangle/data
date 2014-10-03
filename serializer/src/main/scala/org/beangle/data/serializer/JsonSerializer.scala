package org.beangle.data.serializer

import org.beangle.commons.activation.MimeTypes
import org.beangle.data.serializer.converter.{ ConverterRegistry, DefaultConverterRegistry }
import org.beangle.data.serializer.io.StreamDriver
import org.beangle.data.serializer.io.json.JsonDriver
import org.beangle.data.serializer.mapper.{ DefaultMapper, Mapper }
import AbstractSerializer.SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES
import javax.activation.MimeType
import org.beangle.data.serializer.io.json.DefaultJsonDriver

object JsonSerializer {
  import AbstractSerializer._

  def apply(): JsonSerializer = {
    val driver = new DefaultJsonDriver
    val registry = new DefaultConverterRegistry
    driver.registry = registry
    new JsonSerializer(driver, new DefaultMapper, registry).setMode(SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES)
  }
}

class JsonSerializer(driver: JsonDriver, mapper: Mapper, registry: ConverterRegistry)
  extends AbstractSerializer(driver, mapper, registry) {

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationJson)
  }

}