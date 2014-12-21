package org.beangle.data.serialize.marshal

object Type extends Enumeration {
  type Type = TypeValue
  val Number = new TypeValue(true)
  val Boolean = new TypeValue(true)
  val String = new TypeValue(true)
  val Collection = new TypeValue(false)
  val Object = new TypeValue(false)

  class TypeValue(val scalar: Boolean) extends Val {
  }
}
