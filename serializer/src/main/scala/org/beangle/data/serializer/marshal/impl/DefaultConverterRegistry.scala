package org.beangle.data.serializer.marshal.impl

import java.{ util => ju }

import scala.collection.mutable

import org.beangle.commons.lang.reflect.Reflections
import org.beangle.data.serializer.formatter.FormatterException
import org.beangle.data.serializer.marshal.{ Converter, ConverterRegistry }
import org.beangle.data.serializer.util.Caching

class DefaultConverterRegistry extends ConverterRegistry with Caching {

  private val marshallers = new ju.concurrent.ConcurrentHashMap[Class[_], Converter[_]]

  override def lookup[T](clazz: Class[T]): Converter[T] = {
    var marshaller = marshallers.get(clazz)
    if (null == marshaller) {
      marshaller = searchConverter(clazz)
      if (null == marshaller) throw new FormatterException("No converter specified for " + clazz, null)
      else marshallers.put(clazz, marshaller)
    }
    marshaller.asInstanceOf[Converter[T]]
  }

  override def register[T](marshaller: Converter[T]) {
    val clazz = Reflections.getGenericParamType(marshaller.getClass, classOf[Converter[_]])("T")
    marshallers.put(clazz, marshaller)
  }

  override def flushCache(): Unit = {
    val iterator = marshallers.values.iterator()
    while (iterator.hasNext) {
      iterator.next().asInstanceOf[Tuple2[Class[_], Converter[_]]]._2 match {
        case caching: Caching => caching.flushCache()
        case _ =>
      }
    }
  }

  protected def searchConverter(sourceType: Class[_]): Converter[_] = {
    val interfaces = new mutable.LinkedHashSet[Class[_]]
    val classQueue = new mutable.Queue[Class[_]]
    var marshaller: Converter[_] = null
    classQueue += sourceType
    while (!classQueue.isEmpty) {
      val currentClass = classQueue.dequeue
      val marshaller = marshallers.get(currentClass)
      if (marshaller != null) return marshaller
      val superClass = currentClass.getSuperclass
      if (superClass != null && superClass != classOf[AnyRef]) classQueue += superClass
      for (interfaceType <- currentClass.getInterfaces) addInterfaces(interfaceType, interfaces)
    }
    var iter = interfaces.iterator
    while (iter.hasNext) {
      val interface = iter.next
      val converter = marshallers.get(interface)
      if (converter != null) return converter
    }
    marshallers.get(classOf[AnyRef])
  }

  private def addInterfaces(interfaceType: Class[_], interfaces: mutable.Set[Class[_]]) {
    interfaces.add(interfaceType)
    for (inheritedInterface <- interfaceType.getInterfaces) addInterfaces(inheritedInterface, interfaces)
  }

}