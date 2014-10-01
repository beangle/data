package org.beangle.data.serializer.mapper

class Null {}

trait Mapper {

  def serializedClass(clazz: Class[_]): String

  def aliasForSystemAttribute(name: String): String

  def defaultImplementationOf(clazz: Class[_]): Class[_]

  def serializedMember(clazz: Class[_], memberName: String): String

  def alias(alias: String, clazz: Class[_]): Unit

  def alias(alias: String, className: String): Unit
  
  def aliasUnCamel(classes: Class[_]*): Unit
}
