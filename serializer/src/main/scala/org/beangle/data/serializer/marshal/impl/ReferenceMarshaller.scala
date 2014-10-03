package org.beangle.data.serializer.marshal.impl

import org.beangle.commons.lang.Strings
import org.beangle.data.serializer.converter.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.{ Id, MarshallingContext }
import org.beangle.data.serializer.io.Path
import org.beangle.data.serializer.io.AbstractWriter
import org.beangle.data.serializer.io.PathStack

object ReferenceMarshaller {
  val RELATIVE = 0
  val ABSOLUTE = 1
  val SINGLE_NODE = 2
}

abstract class ReferenceMarshaller(registry: ConverterRegistry, mapper: Mapper) extends TreeMarshaller(registry, mapper) {

  override def convert(item: Object, writer: StreamWriter, converter: Converter[Object], context: MarshallingContext): Unit = {
    if (converter.targetType.scalar) {
      // strings, ints, dates, etc... don't bother using references.
      converter.marshal(item, writer, context)
    } else {
      val currentPath = writer.currentPath
      val existed = context.references.get(item)
      if (existed != null) {
        val referAttrName = mapper.aliasForSystemAttribute("reference")
        if (referAttrName != null) writer.addAttribute(referAttrName, createReference(currentPath, existed.key, context))
      } else {
        val newReferKey = createReferenceKey(currentPath, item, context)
        fireValidReference(newReferKey, context)
        context.references.put(item, new Id(newReferKey, currentPath))
        converter.marshal(item, writer, context)
      }
    }
  }

  protected override def createMarshallingContext(writer: StreamWriter, registry: ConverterRegistry): MarshallingContext = {
    writer.asInstanceOf[AbstractWriter].pathStack = new PathStack
    return new MarshallingContext(this, writer, registry)
  }

  protected def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String
  protected def createReferenceKey(currentPath: Path, item: Object, context: MarshallingContext): Object
  protected def fireValidReference(referenceKey: Object, context: MarshallingContext): Unit
}

class ReferenceByXPathMarshaller(registry: ConverterRegistry, mapper: Mapper, mode: Int) extends ReferenceMarshaller(registry, mapper) {

  import ReferenceMarshaller._
  protected override def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String = {
    val existingPath = existedKey.asInstanceOf[Path]
    val referencePath =
      if ((mode & ABSOLUTE) > 0) existingPath
      else currentPath.relativeTo(existingPath)
    return if ((mode & SINGLE_NODE) > 0) referencePath.explicit() else referencePath.toString
  }

  protected override def createReferenceKey(currentPath: Path, item: Object, context: MarshallingContext): AnyRef = {
    return currentPath
  }

  protected override def fireValidReference(referenceKey: Object, context: MarshallingContext) {
  }
}

class ReferenceByIdMarshaller(registry: ConverterRegistry, mapper: Mapper) extends ReferenceMarshaller(registry, mapper) {

  protected override def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String = {
    existedKey.toString
  }

  protected override def createReferenceKey(currentPath: Path, item: Object, context: MarshallingContext): AnyRef = {
    Strings.unCamel(item.getClass().getSimpleName) + "_" + System.identityHashCode(item)
  }

  protected override def fireValidReference(referenceKey: Object, context: MarshallingContext) {
    val attributeName = mapper.aliasForSystemAttribute("id")
    if (attributeName != null) context.writer.addAttribute(attributeName, referenceKey.toString())
  }
}