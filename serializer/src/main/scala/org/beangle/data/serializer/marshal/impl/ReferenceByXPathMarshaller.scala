package org.beangle.data.serializer.marshal.impl

import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.mapper.Mapper
import org.beangle.data.serializer.io.path.Path
import org.beangle.data.serializer.marshal.ConverterRegistry
import org.beangle.data.serializer.marshal.MarshallingContext

class ReferenceByXPathMarshaller(registry: ConverterRegistry, mapper: Mapper, mode: Int) extends AbstractReferenceMarshaller(registry, mapper) {

  import ReferenceMarshaller._
  protected override def createReference(currentPath: Path, existingReferenceKey: Object, context: MarshallingContext): String = {
    val existingPath = existingReferenceKey.asInstanceOf[Path]
    val referencePath =
      if ((mode & ABSOLUTE) > 0) existingPath
      else currentPath.relativeTo(existingPath)

    return if ((mode & SINGLE_NODE) > 0) referencePath.explicit() else referencePath.toString
  }

  protected override def createReferenceKey(currentPath: Path, item: Object, context: MarshallingContext): Object = {
    return currentPath;
  }

  protected override def fireValidReference(referenceKey: Object, context: MarshallingContext) {
    // nothing to do
  }
}
