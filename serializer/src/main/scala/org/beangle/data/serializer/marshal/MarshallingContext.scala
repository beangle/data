package org.beangle.data.serializer.marshal

import org.beangle.data.serializer.converter.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.SerializeException
import org.beangle.data.serializer.io.StreamWriter
import org.beangle.data.serializer.io.path.{ Path, PathTracker }
import org.beangle.data.serializer.util.ObjectIdDictionary

class MarshallingContext(val marshaller: Marshaller, val writer: StreamWriter, registry: ConverterRegistry) {

  val references = new ObjectIdDictionary()
  val implicitElements = new ObjectIdDictionary()
  val pathTracker = new PathTracker()
  var lastPath: Path = _
  val parentObjects = new ObjectIdDictionary()

  def convert(item: Object, writer: StreamWriter): Unit = {
    convert(item, writer, null)
  }

  def convert(item: Object, writer: StreamWriter, converter: Converter[Object]): Unit = {
    if (converter == null) {
      marshaller.convert(item, writer, registry.lookup(item.getClass.asInstanceOf[Class[Object]]), this)
    } else {
      marshaller.convert(item, writer, converter, this)
    }
  }
  def replace(newReferKey: Object, original: Object, replacement: Object) {
    references.associateId(replacement, new Id(newReferKey, lastPath))
  }

  def lookupReference(item: Object): Object = {
    references.lookupId(item).asInstanceOf[Id].item
  }

  def registerImplicit(newReferKey: Object, item: Object): Unit = {
    if (implicitElements.containsId(item)) throw new ReferencedImplicitElementException(item, lastPath)
    implicitElements.associateId(item, newReferKey)
  }
}

class CircularReferenceException(msg: String) extends SerializeException(msg) {
}
class Id(val item: AnyRef, val path: Path)
class ReferencedImplicitElementException(item: Object, path: Path) extends SerializeException("Cannot reference implicit element") {
  add("implicit-element", item.toString())
  add("referencing-element", path.toString())
}