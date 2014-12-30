package org.beangle.data.serialize

import org.beangle.commons.activation.MimeTypes
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.serialize.io.csv.{ CsvDriver, DefaultCsvDriver }
import org.beangle.data.serialize.mapper.{ DefaultMapper, Mapper }
import org.beangle.data.serialize.marshal.{ DefaultMarshallerRegistry, MarshallerRegistry, MarshallingContext }
import javax.activation.MimeType
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.commons.collection.page.Page

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

  override def serialize(item: Object, writer: StreamWriter, params: Map[String, Any]): Unit = {
    val datas = item match {
      case null => null
      case page: Page[_] => page.items
      case _ => item
    }

    val context = new MarshallingContext(this, writer, registry, params)
    writer.start(context)
    if (datas == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      writer.startNode(mapper.serializedClass(datas.getClass()), datas.getClass())
      context.marshal(datas, null)
    }
    writer.endNode()
    writer.end(context)
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

  override def hierarchical: Boolean = {
    false
  }
}