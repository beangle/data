package org.beangle.data.serialize.marshal

import org.beangle.commons.collection.IdentityCache
import org.beangle.data.serialize.StreamSerializer
import org.beangle.data.serialize.io.{ Path, StreamWriter }
import org.beangle.data.serialize.model.Person
import org.beangle.commons.lang.reflect.BeanManifest
import collection.mutable

class MarshallingContext(val serializer: StreamSerializer, val writer: StreamWriter, val registry: MarshallerRegistry, properties: Map[Class[_], List[String]]) {

  val references = new IdentityCache[AnyRef, Id]

  var beanType: Class[_] = _

  val propertyMap = new collection.mutable.HashMap[Class[_], List[String]] ++ properties

  def getProperties(clazz: Class[_]): Option[List[String]] = {
    propertyMap.get(clazz) match {
      case Some(p) => Some(p)
      case None => {
        val p = searchProperties(clazz)
        if (null == p) {
          if (registry.lookup(clazz).targetType == Type.Object) {
            val np = BeanManifest.get(clazz).getters.keySet.toList
            propertyMap.put(clazz, np)
            Some(np)
          } else {
            None
          }
        } else {
          Some(p)
        }
      }
    }
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

  def marshal(item: Object, marshaller: Marshaller[Object] = null): Unit = {
    if (marshaller == null) {
      serializer.marshal(item, registry.lookup(item.getClass.asInstanceOf[Class[Object]]), this)
    } else {
      serializer.marshal(item, marshaller, this)
    }
  }

  def lookupReference(item: Object): Id = {
    references.get(item)
  }
}

class Id(val key: AnyRef, val path: Path) {
  override def toString: String = {
    path.toString()
  }
}