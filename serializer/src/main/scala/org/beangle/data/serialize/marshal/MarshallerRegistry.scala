package org.beangle.data.serialize.marshal

import org.beangle.data.serialize.mapper.Mapper

trait MarshallerRegistry {

  def lookup[T](clazz: Class[T]): Marshaller[T]

  def register[T](converter: Marshaller[T]): Unit
  
}