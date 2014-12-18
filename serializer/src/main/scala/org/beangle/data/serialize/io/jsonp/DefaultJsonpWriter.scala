package org.beangle.data.serialize.io.jsonp

import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.data.serialize.marshal.MarshallerRegistry
import java.io.Writer
import org.beangle.data.serialize.io.json.DefaultJsonWriter

class DefaultJsonpWriter(writer: Writer, registry: MarshallerRegistry) extends DefaultJsonWriter(writer, registry) {
  var callbackName = "callback"
  override def start(context: MarshallingContext): Unit = {
    val callback = context.params.get(callbackName).getOrElse("callback").asInstanceOf[String]
    writer.write(callback)
    writer.write('(')
  }

  override def end(context: MarshallingContext): Unit = {
    writer.write(')')
  }
}