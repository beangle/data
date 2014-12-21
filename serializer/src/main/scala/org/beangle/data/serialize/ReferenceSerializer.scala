package org.beangle.data.serialize

import org.beangle.commons.lang.Strings
import org.beangle.data.serialize.marshal.{ Marshaller, MarshallerRegistry }
import org.beangle.data.serialize.io.StreamWriter
import org.beangle.data.serialize.mapper.Mapper
import org.beangle.data.serialize.marshal.{ Id, MarshallingContext }
import org.beangle.data.serialize.io.Path
import org.beangle.data.serialize.io.AbstractWriter
import org.beangle.data.serialize.io.PathStack
import org.beangle.data.serialize.io.StreamDriver

abstract class ReferenceSerializer extends AbstractSerializer {

  override def marshal(item: Object, marshaller: Marshaller[Object], context: MarshallingContext): Unit = {
    val writer = context.writer
    if (marshaller.targetType.scalar) {
      // strings, ints, dates, etc... don't bother using references.
      marshaller.marshal(item, writer, context)
    } else {
      val currentPath = writer.currentPath
      val existed = context.references.get(item)
      if (existed != null) {
        val referAttrName = mapper.aliasForSystemAttribute("reference")
        if (referAttrName != null) writer.addAttribute(referAttrName, createReference(currentPath, existed.key, context))
      } else {
        val newReferKey = fireReference(currentPath, item, context)
        context.references.put(item, new Id(newReferKey, currentPath))
        marshaller.marshal(item, writer, context)
      }
    }
  }

  protected def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String
  protected def fireReference(currentPath: Path, item: Object, context: MarshallingContext): Object
}

abstract class ReferenceByIdSerializer extends ReferenceSerializer {

  protected override def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String = {
    existedKey.toString
  }

  protected override def fireReference(currentPath: Path, item: Object, context: MarshallingContext): AnyRef = {
    val key = Strings.unCamel(item.getClass().getSimpleName) + "_" + System.identityHashCode(item)
    val attributeName = mapper.aliasForSystemAttribute("id")
    if (attributeName != null) context.writer.addAttribute(attributeName, key.toString())
    key
  }

}

abstract class ReferenceByXPathSerializer(val absolutePath: Boolean, val singleNode: Boolean) extends ReferenceSerializer {

  protected override def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String = {
    val existingPath = existedKey.asInstanceOf[Path]
    val referencePath = if (absolutePath) existingPath else currentPath.relativeTo(existingPath)
    return if (singleNode) referencePath.explicit() else referencePath.toString
  }

  protected override def fireReference(currentPath: Path, item: Object, context: MarshallingContext): AnyRef = {
    return currentPath
  }
}