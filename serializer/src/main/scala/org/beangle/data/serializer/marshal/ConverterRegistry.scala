package org.beangle.data.serializer.marshal

trait ConverterRegistry {

  def lookup[T](clazz: Class[T]): Converter[T]

  def register[T](marshaller: Converter[T]): Unit

}