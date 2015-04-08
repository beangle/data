/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.serialize.marshal

import scala.collection.mutable
import scala.language.existentials

import org.beangle.commons.collection.IdentityCache
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.data.serialize.SerializeException
import org.beangle.data.serialize.mapper.Mapper

class DefaultMarshallerRegistry(mapper: Mapper) extends MarshallerRegistry {

  private val cache = new IdentityCache[Class[_], Marshaller[_]]
  /**
   * [Object,List(BeanMarshaller,PrimitiveMarshaller)]
   */
  private val converterMap = new mutable.HashMap[Class[_], Set[Marshaller[_]]]

  registerBuildin()

  override def lookup[T](clazz: Class[T]): Marshaller[T] = {
    var converter = cache.get(clazz)
    if (null == converter) {
      converter = searchMarshaller(clazz)
      if (null == converter) throw new SerializeException("No converter specified for " + clazz, null)
      else cache.put(clazz, converter)
    }
    converter.asInstanceOf[Marshaller[T]]
  }

  override def register[T](converter: Marshaller[T]) {
    val clazz = Reflections.getGenericParamType(converter.getClass, classOf[Marshaller[_]])("T")
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

  private def registerBuildin(): Unit = {
    register(new CollectionMarshaller(mapper))
    register(new IterableMarshaller(mapper))
    register(new MapMarshaller(mapper))
    register(new JavaMapMarshaller(mapper))
    register(new JavaMapEntryMarshaller(mapper))
    register(new BeanMarshaller(mapper))
    register(new ArrayMarshaller(mapper))
    register(new TupleConvertor(mapper))
    register(new PropertiesMarshaller(mapper))
    register(new PageMarshaller(mapper))
    register(new JsonObjectMarshaller(mapper))
    register(new NumberMarshaller)
    register(new BooleanMarshaller)
    register(new DateMarshaller)
    register(new SqlDateMarshaller)
    register(new CalendarMarshaller)
    register(new TimestampMarshaller)
    register(new TimeMarshaller)
    register(new EnumMarshaller)
  }

  private def searchMarshaller(sourceType: Class[_]): Marshaller[_] = {
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
    if (converter != null) converter
    else {
      if (sourceType.isArray) new ArrayMarshaller(mapper)
      else ObjectMarshaller
    }
  }

  private def searchSupport(clazz: Class[_], converterSet: Option[Set[Marshaller[_]]]): Marshaller[_] = {
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