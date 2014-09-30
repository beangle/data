package org.beangle.data.serializer.io

trait NameCoder {

  /**
   * Encode an object name for a node in the target format.
   */
  def encodeNode(name: String): String

  /**
   * Encode a meta-data name for an attribute in the target format.
   */
  def encodeAttribute(name: String): String
}

object NoNameCoder extends NameCoder {
  
  override def encodeNode(name: String): String = name

  override def encodeAttribute(name: String): String = name
}