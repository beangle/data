package org.beangle.data.serialize

import java.io.{ OutputStream, StringWriter }
import java.{ util => ju }
import org.beangle.commons.activation.MimeTypes
import org.beangle.commons.bean.PropertyUtils
import org.beangle.commons.io.Serializer
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.marshal.{ MarshallerRegistry, DefaultMarshallerRegistry }
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.io.csv.{ CsvDriver, DefaultCsvWriter }
import org.beangle.data.serialize.mapper.{ DefaultMapper, Mapper }
import javax.activation.MimeType
import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.data.serialize.marshal.Marshaller
import org.beangle.data.serialize.marshal.Id

object CsvSerializer {

  def apply(): CsvSerializer = {
    val mapper = new DefaultMapper
    val registry = new DefaultMarshallerRegistry(mapper)
    new CsvSerializer(new CsvDriver(), mapper, registry)
  }
}

final class CsvSerializer(val driver: CsvDriver, val mapper: Mapper, val registry: MarshallerRegistry) extends ReferenceByIdSerializer {

  override def marshal(item: Object, marshaller: Marshaller[Object], context: MarshallingContext): Unit = {
    val writer = context.writer
    if (marshaller.targetType.scalar) {
      // strings, ints, dates, etc... don't bother using references.
      marshaller.marshal(item, writer, context)
    } else {
      val id = context.references.get(item)
      if (id == null) {
        val currentPath = writer.currentPath
        val newReferKey = createReferenceKey(currentPath, item, context)
        context.references.put(item, new Id(newReferKey, currentPath))
        marshaller.marshal(item, writer, context)
      } else {
        context.marshal(id.toString)
      }
    }
  }

  def supportMediaTypes: Seq[MimeType] = {
    List(MimeTypes.TextCsv)
  }
}