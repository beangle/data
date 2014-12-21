package org.beangle.data.serialize

import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.data.serialize.marshal.Marshaller
import org.beangle.commons.io.Serializer

trait StreamSerializer extends Serializer {

  def serialize(obj: Object, writer: StreamWriter, params: Map[String, Any])

  def marshalNull(obj: Object, property: String, context: MarshallingContext)

  def marshal(item: Object, marshaller: Marshaller[Object], context: MarshallingContext): Unit
}