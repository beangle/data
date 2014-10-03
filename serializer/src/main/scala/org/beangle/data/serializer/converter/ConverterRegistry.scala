package org.beangle.data.serializer.converter

import org.beangle.data.serializer.mapper.Mapper

trait ConverterRegistry {

  def lookup[T](clazz: Class[T]): Converter[T]

  def register[T](converter: Converter[T]): Unit

  def registerBuildin(mapper: Mapper): Unit
  
}