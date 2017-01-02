/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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

import org.beangle.commons.collection.{ IdentityMap, IdentitySet }
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.serialize.StreamSerializer
import org.beangle.data.serialize.io.{ Path, StreamWriter }

class MarshallingContext(val serializer: StreamSerializer, val writer: StreamWriter, val registry: MarshallerRegistry, val params: Map[String, Any]) {

  val references = new IdentityMap[AnyRef, Id]

  val currents = new IdentitySet[AnyRef]

  var elementType: Class[_] = _

  val propertyMap = new collection.mutable.HashMap[Class[_], List[String]]

  init()

  def init(): Unit = {
    val properties = params.get("properties").getOrElse(List.empty).asInstanceOf[Seq[Tuple2[Class[_], List[String]]]]
    if (serializer.hierarchical) {
      propertyMap ++= properties
    } else {
      properties foreach { tuple =>
        val getters = BeanInfos.get(tuple._1).readables
        val filted = new collection.mutable.ListBuffer[String]
        tuple._2 foreach { p =>
          getters.get(p) match {
            case Some(getter) => if (!isCollectionType(getter.clazz)) filted += p
            case None         => throw new RuntimeException("cannot find property $p of class ${tuple2._1.getName}")
          }
        }
        propertyMap.put(tuple._1, filted.toList)
      }
    }
    params.get("elementType") match {
      case Some(cls) =>
        elementType = cls.asInstanceOf[Class[_]]
        //search bean type (clazz maybe interface,so cache it first,ready for concret class)
        getProperties(elementType)
      case None =>
    }
    if (!properties.isEmpty && null == elementType) elementType = properties.head._1
  }

  def getProperties(clazz: Class[_]): List[String] = {
    val result = propertyMap.get(clazz) match {
      case Some(p) => p
      case None => {
        if (registry.lookup(clazz).targetType == Type.Object) {
          val p = searchProperties(clazz)
          if (null == p) {
            val readables = BeanInfos.get(clazz).readables
            val bp =
              if (readables.contains("id") && null != elementType && elementType != clazz) {
                List("id")
              } else {
                if (serializer.hierarchical) {
                  readables.filter(!_._2.isTransient).keySet.toList
                } else {
                  readables.filter { g => !g._2.isTransient && !isCollectionType(g._2.clazz) }.keySet.toList
                }
              }
            propertyMap.put(clazz, bp)
            bp
          } else {
            p
          }
        } else {
          List()
        }
      }
    }
    if (elementType == null && !isCollectionType(clazz)) elementType = clazz
    result
  }

  private def searchProperties(targetType: Class[_]): List[String] = {
    val interfaces = new mutable.LinkedHashSet[Class[_]]
    val classQueue = new mutable.Queue[Class[_]]
    classQueue += targetType
    while (!classQueue.isEmpty) {
      val currentClass = classQueue.dequeue
      val props = propertyMap.get(currentClass).orNull
      if (props != null) return props
      val superClass = currentClass.getSuperclass
      if (superClass != null && superClass != classOf[AnyRef]) classQueue += superClass
      for (interfaceType <- currentClass.getInterfaces) addInterfaces(interfaceType, interfaces)
    }
    var iter = interfaces.iterator
    while (iter.hasNext) {
      val interfaceType = iter.next
      val props = propertyMap.get(interfaceType).orNull
      if (props != null) return props
    }
    null
  }

  private def addInterfaces(interfaceType: Class[_], interfaces: mutable.Set[Class[_]]) {
    interfaces.add(interfaceType)
    for (inheritedInterface <- interfaceType.getInterfaces) addInterfaces(inheritedInterface, interfaces)
  }

  def marshal(item: Any, marshaller: Marshaller[Any] = null): Unit = {
    if (marshaller == null) {
      serializer.marshal(item, registry.lookup(item.getClass.asInstanceOf[Class[Any]]), this)
    } else {
      serializer.marshal(item, marshaller, this)
    }
  }

  def marshalNull(item: Object, property: String): Unit = {
    serializer.marshalNull(item, property, this)
  }

  def lookupReference(item: Object): Id = {
    references.get(item)
  }

  private def isCollectionType(clazz: Class[_]): Boolean = {
    clazz.isArray() || classOf[java.util.Collection[_]].isAssignableFrom(clazz) || classOf[Iterable[_]].isAssignableFrom(clazz)
  }

}

class Id(val key: AnyRef, val path: Path) {
  override def toString: String = {
    path.toString()
  }
}
