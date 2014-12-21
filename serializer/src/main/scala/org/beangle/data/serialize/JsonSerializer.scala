package org.beangle.data.serialize

import org.beangle.commons.activation.MimeTypes
import org.beangle.data.serialize.io.json.{ DefaultJsonDriver, JsonDriver }
import org.beangle.data.serialize.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serialize.marshal.{ DefaultMarshallerRegistry, MarshallerRegistry }

import javax.activation.MimeType

object JsonSerializer {

  def apply(): JsonSerializer = {
    val driver = new DefaultJsonDriver
    val mapper = new DefaultMapper
    val registry = new DefaultMarshallerRegistry(mapper)
    driver.registry = registry
    driver.compressOutput = false
    new JsonSerializer(driver, mapper, registry)
  }
}

class JsonSerializer(val driver: JsonDriver, val mapper: Mapper, val registry: MarshallerRegistry)
  extends AbstractSerializer {

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationJson)
  }

}