package org.beangle.data.serialize

import org.beangle.commons.activation.MimeTypes
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.io.csv.{ CsvDriver, DefaultCsvDriver }
import org.beangle.data.serialize.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serialize.marshal.{ DefaultMarshallerRegistry, MarshallerRegistry, MarshallingContext }

import javax.activation.MimeType

object CsvSerializer {
  def apply(): CsvSerializer = {
    val mapper = new DefaultMapper
    val registry = new DefaultMarshallerRegistry(mapper)
    new CsvSerializer(new DefaultCsvDriver(), mapper, registry)
  }
}

final class CsvSerializer(val driver: CsvDriver, val mapper: Mapper, val registry: MarshallerRegistry) extends AbstractSerializer {

  def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.TextCsv)
  }

  override def marshalNull(obj: Object, property: String, context: MarshallingContext): Unit = {
    val size = context.getProperties(BeanManifest.get(obj.getClass).getGetter(property).get.returnType).size
    if (size > 0) {
      (0 until size) foreach { i =>
        context.writer.setValue("")
      }
    } else {
      context.writer.setValue("")
    }
  }
}