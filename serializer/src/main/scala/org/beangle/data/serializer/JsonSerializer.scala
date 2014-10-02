package org.beangle.data.serializer

import java.io.{ OutputStream, StringWriter, Writer }
import org.beangle.commons.activation.MimeTypes
import org.beangle.commons.io.Serializer
import org.beangle.data.serializer.converter.ConverterRegistry
import org.beangle.data.serializer.io.StreamDriver
import org.beangle.data.serializer.mapper.Mapper
import javax.activation.MimeType
import org.beangle.data.serializer.mapper.DefaultMapper
import org.beangle.data.serializer.converter.DefaultConverterRegistry

object JsonSerializer {
  import AbstractSerializer._
  def apply(driver: StreamDriver): XmlSerializer = {
    new XmlSerializer(driver, new DefaultMapper, new DefaultConverterRegistry).setMode(SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES)
  }
}

class JsonSerializer(streamDriver: StreamDriver, mapper: Mapper, registry: ConverterRegistry)
  extends AbstractSerializer(streamDriver, mapper, registry) {

  override def supportedMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationJson)
  }

}