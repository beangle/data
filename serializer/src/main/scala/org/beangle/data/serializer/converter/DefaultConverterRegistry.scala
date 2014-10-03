package org.beangle.data.serializer.converter

import java.{ util => ju }

import scala.collection.mutable

import org.beangle.commons.lang.reflect.Reflections
import org.beangle.data.serializer.SerializeException
import org.beangle.data.serializer.mapper.Mapper

class DefaultConverterRegistry extends ConverterRegistry {

  private val cache = new ju.concurrent.ConcurrentHashMap[Class[_], Converter[_]]
  /**
   * [Object,List(BeanConverter,PrimitiveConverter)]
   */
  private val converterMap = new mutable.HashMap[Class[_], Set[Converter[_]]]

  override def lookup[T](clazz: Class[T]): Converter[T] = {
    var converter = cache.get(clazz)
    if (null == converter) {
      converter = searchConverter(clazz)
      if (null == converter) throw new SerializeException("No converter specified for " + clazz, null)
      else cache.put(clazz, converter)
    }
    converter.asInstanceOf[Converter[T]]
  }

  override def register[T](converter: Converter[T]) {
    val clazz = Reflections.getGenericParamType(converter.getClass, classOf[Converter[_]])("T")
    converterMap.get(clazz) match {
      case Some(converters) =>
        converters.find { each =>
          each.getClass() == converter.getClass()
        } match {
          case Some(c) => converterMap.put(clazz, converters - c + converter)
          case None => converterMap.put(clazz, converters + converter)
        }
      case None => converterMap.put(clazz, Set(converter))
    }
  }

  override def registerBuildin(mapper: Mapper): Unit = {
    register(new CollectionConverter(mapper))
    register(new IterableConverter(mapper))
    register(new MapConverter(mapper))
    register(new JavaMapConverter(mapper))
    register(new BeanConverter(mapper))
    register(new TupleConvertor(mapper))
    register(new NumberConverter)
    register(new BooleanConverter)
    register(new DateConverter)
    register(new SqlDateConverter)
    register(new CalendarConverter)
    register(new TimestampConverter)
    register(new TimeConverter)
  }

  private def searchConverter(sourceType: Class[_]): Converter[_] = {
    val interfaces = new mutable.LinkedHashSet[Class[_]]
    val classQueue = new mutable.Queue[Class[_]]
    classQueue += sourceType
    while (!classQueue.isEmpty) {
      val currentClass = classQueue.dequeue
      val converter = searchSupport(sourceType, converterMap.get(currentClass))
      if (converter != null) return converter
      val superClass = currentClass.getSuperclass
      if (superClass != null && superClass != classOf[AnyRef]) classQueue += superClass
      for (interfaceType <- currentClass.getInterfaces) addInterfaces(interfaceType, interfaces)
    }
    var iter = interfaces.iterator
    while (iter.hasNext) {
      val interface = iter.next
      val converter = searchSupport(sourceType, converterMap.get(interface))
      if (converter != null) return converter
    }

    val converter = searchSupport(sourceType, converterMap.get(classOf[AnyRef]))
    if (converter != null) converter else ObjectConverter
  }

  private def searchSupport(clazz: Class[_], converterSet: Option[Set[Converter[_]]]): Converter[_] = {
    converterSet match {
      case Some(converters) =>
        val iter = converters.iterator
        while (iter.hasNext) {
          val converter = iter.next()
          if (converter.support(clazz)) return converter
        }
        null
      case None => null
    }
  }

  private def addInterfaces(interfaceType: Class[_], interfaces: mutable.Set[Class[_]]) {
    interfaces.add(interfaceType)
    for (inheritedInterface <- interfaceType.getInterfaces) addInterfaces(inheritedInterface, interfaces)
  }

}