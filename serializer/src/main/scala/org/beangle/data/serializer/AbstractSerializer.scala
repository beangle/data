package org.beangle.data.serializer

import org.beangle.data.serializer.marshal.Marshaller
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.io.StreamDriver
import org.beangle.data.serializer.converter.ConverterRegistry
import java.io.StringWriter
import org.beangle.data.serializer.marshal.impl.ReferenceByIdMarshaller
import org.beangle.data.serializer.marshal.impl.ReferenceByXPathMarshaller
import org.beangle.data.serializer.marshal.impl.TreeMarshaller
import org.beangle.data.serializer.marshal.impl.ReferenceMarshaller
import java.io.Writer
import java.io.OutputStream
import org.beangle.commons.io.Serializer

object AbstractSerializer {
  val NO_REFERENCES = 1001
  val ID_REFERENCES = 1002
  val XPATH_RELATIVE_REFERENCES = 1003
  val XPATH_ABSOLUTE_REFERENCES = 1004
  val SINGLE_NODE_XPATH_RELATIVE_REFERENCES = 1005
  val SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES = 1006
}
abstract class AbstractSerializer(val streamDriver: StreamDriver, val mapper: Mapper, val registry: ConverterRegistry) extends Serializer {

  protected var marshaller: Marshaller = _

  registry.registerBuildin(mapper)

  def marshal(obj: Object, writer: StreamWriter): Unit = {
    marshaller.marshal(obj, writer)
  }

  def alias(alias: String, clazz: Class[_]): Unit = {
    mapper.alias(alias, clazz)
  }

  def alias(alias: String, className: String): Unit = {
    mapper.alias(alias, className)
  }

  override def serialize(obj: AnyRef, out: OutputStream): Unit = {
    val writer = streamDriver.createWriter(out)
    try {
      marshal(obj, writer)
    } finally {
      writer.flush()
    }
  }

  def serialize(obj: Object): String = {
    val writer = new StringWriter()
    serialize(obj, writer)
    writer.toString()
  }

  def serialize(obj: Object, out: Writer) {
    val writer = streamDriver.createWriter(out)
    try {
      marshal(obj, writer)
    } finally {
      writer.flush()
    }
  }

  def setMode(mode: Int): this.type = {
    import ReferenceMarshaller._
    import AbstractSerializer._
    marshaller =
      mode match {
        case NO_REFERENCES => new TreeMarshaller(registry, mapper)
        case ID_REFERENCES => new ReferenceByIdMarshaller(registry, mapper)
        case XPATH_RELATIVE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, RELATIVE)
        case XPATH_ABSOLUTE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, ABSOLUTE)
        case SINGLE_NODE_XPATH_RELATIVE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, RELATIVE | SINGLE_NODE)
        case SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, ABSOLUTE | SINGLE_NODE)
        case _ =>
          throw new IllegalArgumentException("Unknown mode : " + mode)
      }
    this
  }
}