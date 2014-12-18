package org.beangle.data.serialize

import org.beangle.data.serialize.marshal.{ Marshaller, MarshallerRegistry }
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.data.serialize.marshal.{ Marshaller, MarshallingContext }
import org.beangle.commons.conversion.ConverterRegistry
import org.beangle.data.serialize.io.StreamDriver
import java.io.StringWriter
import java.io.Writer
import java.io.OutputStream
import scala.collection.immutable.TreeMap

abstract class AbstractSerializer extends StreamSerializer {

  def driver: StreamDriver
  def mapper: Mapper
  def registry: MarshallerRegistry

  //  override def marshal(item: Object, writer: StreamWriter, marshaller: Marshaller[Object], context: MarshallingContext): Unit = {
  //    if (marshaller.targetType.scalar) {
  //      marshaller.marshal(item, writer, context)
  //    } else {
  //      val references = context.references
  //      if (references.contains(item)) {
  //        val e = new SerializeException("Recursive reference to parent object")
  //        e.add("item-type", item.getClass().getName())
  //        e.add("converter-type", marshaller.getClass().getName())
  //        throw e
  //      }
  //      references.put(item, null)
  //      marshaller.marshal(item, writer, context)
  //      references.remove(item)
  //    }
  //  }

  def alias(alias: String, clazz: Class[_]): Unit = {
    mapper.alias(alias, clazz)
  }

  def alias(alias: String, className: String): Unit = {
    mapper.alias(alias, className)
  }

  override def serialize(obj: AnyRef, out: OutputStream, properties: Tuple2[Class[_], List[String]]*): Unit = {
    val writer = driver.createWriter(out)
    try {
      serialize(obj, writer, properties: _*)
    } finally {
      writer.flush()
    }
  }

  def serialize(obj: Object, properties: Tuple2[Class[_], List[String]]*): String = {
    val writer = new StringWriter()
    serialize(obj, writer, properties: _*)
    writer.toString()
  }

  def serialize(obj: Object, out: Writer, properties: Tuple2[Class[_], List[String]]*) {
    val writer = driver.createWriter(out)
    try {
      serialize(obj, writer, properties: _*)
    } finally {
      writer.flush()
    }
  }

  override def serialize(item: Object, writer: StreamWriter, properties: Tuple2[Class[_], List[String]]*): Unit = {
    val context = new MarshallingContext(this, writer, registry, properties.toMap)
    if (!properties.isEmpty) context.beanType = properties.head._1
    writer.start(context)
    if (item == null) {
      writer.startNode(mapper.serializedClass(classOf[Null]), classOf[Null])
    } else {
      writer.startNode(mapper.serializedClass(item.getClass()), item.getClass())
      context.marshal(item, null)
    }
    writer.endNode()
    writer.end(context)
  }
}
