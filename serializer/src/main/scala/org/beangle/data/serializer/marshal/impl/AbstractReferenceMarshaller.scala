package org.beangle.data.serializer.marshal.impl

import org.beangle.data.serializer.converter.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.io.path.Path
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.marshal.{ Id, MarshallingContext }

object ReferenceMarshaller {
  val RELATIVE = 0;
  val ABSOLUTE = 1;
  val SINGLE_NODE = 2;
}

abstract class AbstractReferenceMarshaller(registry: ConverterRegistry, mapper: Mapper) extends TreeMarshaller(registry, mapper) {

  override def convert(item: Object, writer: StreamWriter, converter: Converter[Object], context: MarshallingContext): Unit = {
    if (converter.isConverterToLiteral) {
      // strings, ints, dates, etc... don't bother using references.
      converter.marshal(item, writer, context)
    } else {
      val currentPath = context.pathTracker.getPath()
      val existed = context.references.lookupId(item).asInstanceOf[Id]
      if (existed != null && existed.path != currentPath) {
        val attributeName = mapper.aliasForSystemAttribute("reference")
        if (attributeName != null) {
          writer.addAttribute(attributeName, createReference(currentPath, existed.item, context))
        }
      } else {
        val newReferKey = if (existed == null) createReferenceKey(currentPath, item, context) else existed.item
        val lastPath = context.lastPath
        if (lastPath == null || !currentPath.isAncestor(lastPath)) {
          fireValidReference(newReferKey, context)
          context.lastPath = currentPath
          context.references.associateId(item, new Id(newReferKey, currentPath))
        }
        converter.marshal(item, writer, context)
      }
    }
  }

  protected def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String
  protected def createReferenceKey(currentPath: Path, item: Object, context: MarshallingContext): Object
  protected def fireValidReference(referenceKey: Object, context: MarshallingContext): Unit
}
