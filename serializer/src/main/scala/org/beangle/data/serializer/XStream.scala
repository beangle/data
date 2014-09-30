package org.beangle.data.serializer

import java.io.{ OutputStream, StringWriter, Writer }
import org.beangle.data.serializer.io.{ StreamDriver, StreamWriter }
import org.beangle.data.serializer.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serializer.marshal.converter.{ BeanConverter, CollectionConverter, IterableConverter, JavaMapConverter, MapConverter }
import org.beangle.data.serializer.marshal.impl.DefaultConverterRegistry
import org.beangle.data.serializer.marshal.Marshaller
import org.beangle.data.serializer.marshal.DataHolder
import org.beangle.data.serializer.marshal.ConverterRegistry
import org.beangle.data.serializer.marshal.impl.ReferenceByXPathMarshaller
import org.beangle.data.serializer.marshal.impl.TreeMarshaller
import org.beangle.data.serializer.marshal.impl.ReferenceMarshaller

object XStream {
  val NO_REFERENCES = 1001;
  val ID_REFERENCES = 1002;
  val XPATH_RELATIVE_REFERENCES = 1003;
  val XPATH_ABSOLUTE_REFERENCES = 1004;
  val SINGLE_NODE_XPATH_RELATIVE_REFERENCES = 1005;
  val SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES = 1006;

  val PRIORITY_VERY_HIGH = 10000;
  val PRIORITY_NORMAL = 0;
  val PRIORITY_LOW = -10;
  val PRIORITY_VERY_LOW = -20;

  def apply(driver: StreamDriver): XStream = {
    new XStream(driver, new DefaultMapper, new DefaultConverterRegistry).setMode(XPATH_RELATIVE_REFERENCES)
  }

}

class XStream(val streamDriver: StreamDriver, val mapper: Mapper, val registry: ConverterRegistry) {

  import XStream._
  var marshaller: Marshaller = _

  setupMarshallers()

  def toXML(obj: Object): String = {
    val writer = new StringWriter()
    toXML(obj, writer)
    writer.toString()
  }

  def toXML(obj: Object, out: Writer) {
    val writer = streamDriver.createWriter(out);
    try {
      marshal(obj, writer);
    } finally {
      writer.flush();
    }
  }

  def toXML(obj: Object, out: OutputStream) {
    val writer = streamDriver.createWriter(out)
    try {
      marshal(obj, writer);
    } finally {
      writer.flush();
    }
  }

  def marshal(obj: Object, writer: StreamWriter): Unit = {
    marshal(obj, writer, null)
  }

  def marshal(obj: Object, writer: StreamWriter, dataHolder: DataHolder): Unit = {
    marshaller.marshal(obj, writer, dataHolder);
  }

  private def setupMarshallers(): Unit = {
    registry.register(new CollectionConverter(this.mapper))
    registry.register(new IterableConverter(this.mapper))
    registry.register(new MapConverter(this.mapper))
    registry.register(new JavaMapConverter(this.mapper))
    registry.register(new BeanConverter(this.mapper))
  }

  def setMode(mode: Int): this.type = {
    import ReferenceMarshaller._
    marshaller =
      mode match {
        case NO_REFERENCES => new TreeMarshaller(registry, mapper)
        //        case ID_REFERENCES => new ReferenceByIdMarshallingStrategy()
        case XPATH_RELATIVE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, RELATIVE)
        case XPATH_ABSOLUTE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, ABSOLUTE)
        case SINGLE_NODE_XPATH_RELATIVE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, RELATIVE | SINGLE_NODE)
        case SINGLE_NODE_XPATH_ABSOLUTE_REFERENCES => new ReferenceByXPathMarshaller(registry, mapper, ABSOLUTE | SINGLE_NODE)
        case _ =>
          throw new IllegalArgumentException("Unknown mode : " + mode);
      }
    this
  }
}