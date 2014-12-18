package org.beangle.data.serialize.mapper

class Null {}

trait Mapper {

  def serializedClass(clazz: Class[_]): String

  def serializedMember(clazz: Class[_], memberName: String): String

  def aliasForSystemAttribute(name: String): String

  def alias(alias: String, clazz: Class[_]): Unit

  def alias(alias: String, className: String): Unit
  
  def aliasUnCamel(classes: Class[_]*): Unit
}
