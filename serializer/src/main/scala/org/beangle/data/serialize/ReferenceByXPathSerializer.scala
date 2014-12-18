package org.beangle.data.serialize

import org.beangle.data.serialize.marshal.MarshallingContext
import org.beangle.data.serialize.io.Path
import org.beangle.data.serialize.io.StreamDriver
import org.beangle.data.serialize.marshal.MarshallerRegistry
import org.beangle.data.serialize.mapper.Mapper

abstract class ReferenceByXPathSerializer(val absolutePath: Boolean, val singleNode: Boolean) extends ReferenceSerializer {

  protected override def createReference(currentPath: Path, existedKey: Object, context: MarshallingContext): String = {
    val existingPath = existedKey.asInstanceOf[Path]
    val referencePath = if (absolutePath) existingPath else currentPath.relativeTo(existingPath)
    return if (singleNode) referencePath.explicit() else referencePath.toString
  }

  protected override def createReferenceKey(currentPath: Path, item: Object, context: MarshallingContext): AnyRef = {
    return currentPath
  }

  protected override def fireValidReference(referenceKey: Object, context: MarshallingContext) {
  }

}
