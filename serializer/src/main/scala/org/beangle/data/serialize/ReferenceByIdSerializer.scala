package org.beangle.data.serialize

import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.commons.lang.Strings
import org.beangle.data.serialize.io.Path
import org.beangle.data.serialize.io.StreamDriver
import org.beangle.data.serialize.marshal.MarshallerRegistry
import org.beangle.data.serialize.mapper.Mapper

abstract class ReferenceByIdSerializer extends ReferenceSerializer {

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