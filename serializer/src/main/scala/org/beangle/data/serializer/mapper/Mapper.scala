package org.beangle.data.serializer.mapper

class Null {}
trait Mapper {

  def serializedClass(clazz: Class[_]): String

  //def isImmutableValueType(clazz: Class[_]): Boolean

  def aliasForSystemAttribute(name: String): String

  def defaultImplementationOf(clazz: Class[_]): Class[_]

  def serializedMember(clazz: Class[_], memberName: String): String

}

class DefaultMapper extends Mapper {
  override def serializedClass(clazz: Class[_]): String = {
    clazz.getSimpleName()
  }

//  override def isImmutableValueType(clazz: Class[_]): Boolean = {
//    false
//  }

  override def aliasForSystemAttribute(name: String): String = {
    name
  }

  override def defaultImplementationOf(clazz: Class[_]): Class[_] = {
    clazz
  }

  override def serializedMember(clazz: Class[_], memberName: String): String = {
    memberName
  }

}