package org.beangle.data.serializer

import java.io.{ OutputStream, StringWriter, Writer }
import org.beangle.data.serializer.converter.{ ConverterRegistry, DefaultConverterRegistry }
import org.beangle.data.serializer.io.{ StreamDriver, StreamWriter }
import org.beangle.data.serializer.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serializer.marshal.Marshaller
import org.beangle.data.serializer.marshal.impl.{ ReferenceByIdMarshaller, ReferenceByXPathMarshaller }
import org.beangle.data.serializer.marshal.impl.ReferenceMarshaller.{ ABSOLUTE, RELATIVE, SINGLE_NODE }
import org.beangle.data.serializer.marshal.impl.TreeMarshaller
import org.beangle.data.serializer.marshal.impl.ReferenceMarshaller
import javax.activation.MimeType
import org.beangle.commons.io.Serializer
import org.beangle.commons.activation.MimeTypes

object XmlSerializer {

  import AbstractSerializer._
  def apply(driver: StreamDriver): XmlSerializer = {
    new XmlSerializer(driver, new DefaultMapper, new DefaultConverterRegistry).setMode(SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES)
  }
}

class XmlSerializer(streamDriver: StreamDriver, mapper: Mapper, registry: ConverterRegistry)
  extends AbstractSerializer(streamDriver, mapper, registry) with Serializer {

  import XmlSerializer._

  override def supportedMediaTypes: Seq[MimeType] = {
    List(MimeTypes.ApplicationXml)
  }

}