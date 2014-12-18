package org.beangle.data.serialize

import org.beangle.commons.activation.MimeTypes
import org.beangle.data.serialize.io.StreamDriver
import org.beangle.data.serialize.io.jsonp.DefaultJsonpDriver
import org.beangle.data.serialize.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serialize.marshal.{ DefaultMarshallerRegistry, MarshallerRegistry }

import javax.activation.MimeType

object JsonpSerializer {

  def apply(): JsonpSerializer = {
    val driver = new DefaultJsonpDriver
    val mapper = new DefaultMapper
    val registry = new DefaultMarshallerRegistry(mapper)
    driver.registry = registry
    new JsonpSerializer(driver, mapper, registry)
  }
}

class JsonpSerializer(val driver: StreamDriver, val mapper: Mapper, val registry: MarshallerRegistry)
  extends ReferenceByXPathSerializer(true, true) {

  override def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationJson)
  }
}