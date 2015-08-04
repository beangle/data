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
package org.beangle.data.model.bind

import scala.collection.JavaConversions.asScalaSet
import scala.collection.mutable
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.runtime.{ universe => ru }
import org.beangle.commons.lang.annotation.beta
import org.beangle.data.model.bind.Binder.{ Collection, CollectionProperty, Column, Entity, IdGenerator, EntityProxy, Property, SimpleKey, ToManyElement, TypeNameHolder }
import org.beangle.data.model.{ Entity => MEntity, Component => MComponent }
import org.beangle.commons.collection.Collections

object Mapping {

  trait Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit
  }

  class NotNull extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      property.columns foreach (c => c.nullable = false)
    }
  }

  class Unique extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      property.columns foreach (c => c.unique = true)
    }
  }

  class Cache(val cacheholder: CacheHolder) extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      cacheholder.add(List(new Collection(holder.clazz, property.name)))
    }
  }

  class TypeSetter(val typeName: String) extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      property match {
        case th: TypeNameHolder => th.typeName = Some(typeName)
        case _                  => throw new RuntimeException(s"${property.name} is not TypeNameHolder,Cannot specified with typeis")
      }
    }
  }

  class One2Many(val targetEntity: Class[_], val mappedBy: String) extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      property match {
        case collp: CollectionProperty =>
          collp.key = Some(new SimpleKey(new Column(columnName(mappedBy, true))))
          val ele = collp.element.get.asInstanceOf[ToManyElement]
          ele.columns.clear
          ele.one2many = true
          if (null != targetEntity) ele.entityName = targetEntity.getName
        case _ => throw new RuntimeException("order by should used on seq")
      }
    }
  }

  class OrderBy(orderBy: String) extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      property match {
        case collp: CollectionProperty => collp.orderBy = Some(orderBy)
        case _                         => throw new RuntimeException("order by should used on seq")
      }
    }
  }

  class Length(val len: Int) extends Declaration {
    def apply(holder: EntityHolder[_], property: Property): Unit = {
      property.columns foreach (c => c.length = Some(len))
    }
  }

  class Expression(val holder: EntityHolder[_]) {

    def is(declarations: Declaration*): Unit = {
      val lasts = holder.proxy.lastAccessed()
      import collection.JavaConversions.asScalaSet
      for (property <- lasts; declaration <- declarations) {
        val p = holder.entity.getProperty(property)
        p.mergeable = false
        declaration(holder, p)
      }
      lasts.clear()
    }

    def &(next: Expression): Expressions = {
      new Expressions(holder)
    }
  }

  class Expressions(val holder: EntityHolder[_]) {
    def &(next: Expression): this.type = {
      this
    }

    def are(declarations: Declaration*): Unit = {
      val lasts = holder.proxy.lastAccessed()
      import collection.JavaConversions.asScalaSet
      for (property <- lasts; declaration <- declarations) {
        val p = holder.entity.getProperty(property)
        p.mergeable = false
        declaration(holder, p)
      }
      lasts.clear()
    }
  }

  final class EntityHolder[T](val entity: Entity, val binder: Binder, val clazz: Class[T], module: Mapping) {

    var proxy: EntityProxy = _

    def cacheable(): this.type = {
      entity.cache(module.cacheConfig.region, module.cacheConfig.usage)
      this
    }

    def cache(region: String): this.type = {
      entity.cacheRegion = region
      this
    }

    def usage(usage: String): this.type = {
      entity.cacheUsage = usage
      this
    }

    def on(declarations: T => Any)(implicit manifest: Manifest[T]): this.type = {
      if (null == proxy) proxy = binder.generateProxy(clazz)
      declarations(proxy.asInstanceOf[T])
      this
    }

    def generator(strategy: String): this.type = {
      entity.idGenerator = Some(new IdGenerator(strategy))
      this
    }
  }

  final class CacheConfig(var region: String = null, var usage: String = null) {
  }

  final class CacheHolder(val binder: Binder, val cacheRegion: String, val cacheUsage: String) {
    def add(first: List[Collection], definitionLists: List[Collection]*): this.type = {
      first.foreach(d => binder.addCollection(d.cache(cacheRegion, cacheUsage)))
      for (definitions <- definitionLists) {
        definitions.foreach(d => binder.addCollection(d.cache(cacheRegion, cacheUsage)))
      }
      this
    }

    def add(first: Class[_ <: MEntity[_]], classes: Class[_ <: MEntity[_]]*): this.type = {
      binder.getEntity(first).cache(cacheRegion, cacheUsage)
      for (clazz <- classes)
        binder.getEntity(clazz).cache(cacheRegion, cacheUsage)
      this
    }
  }

  final class Entities(val entities: collection.mutable.Map[String, Entity], cacheConfig: CacheConfig) {
    def except(clazzes: Class[_]*): this.type = {
      clazzes foreach { c => entities -= c.getName }
      this
    }

    def cacheable(): Unit = {
      entities foreach { e =>
        e._2.cacheRegion = cacheConfig.region
        e._2.cacheUsage = cacheConfig.usage
      }
    }

    def cache(region: String): this.type = {
      entities foreach (e => e._2.cacheRegion = region)
      this
    }

    def usage(usage: String): this.type = {
      entities foreach (e => e._2.cacheUsage = usage)
      this
    }
  }

  def columnName(propertyName: String, key: Boolean = false): String = {
    val lastDot = propertyName.lastIndexOf(".")
    val columnName = if (lastDot == -1) s"@${propertyName}" else "@" + propertyName.substring(lastDot + 1)
    if (key) columnName + "Id" else columnName
  }

}

@beta
abstract class Mapping {

  import Mapping._
  private var currentHolder: EntityHolder[_] = _
  private var defaultIdGenerator: Option[String] = None
  private val cacheConfig = new CacheConfig()

  private var entities = Collections.newMap[String, Entity]

  import scala.language.implicitConversions

  implicit def any2Expression(i: Any): Expression = {
    new Expression(currentHolder)
  }

  private var binder: Binder = _

  def binding(): Unit

  protected def declare[B](a: B*): Seq[B] = {
    a
  }

  protected def notnull = new NotNull

  protected def unique = new Unique

  protected def length(len: Int) = new Length(len)

  protected def cacheable: Cache = {
    new Cache(new CacheHolder(binder, cacheConfig.region, cacheConfig.usage))
  }

  protected def cacheable(region: String, usage: String): Cache = {
    new Cache(new CacheHolder(binder, region, usage))
  }

  protected def one2many(mappedBy: String): One2Many = {
    new One2Many(null, mappedBy)
  }

  protected def orderby(orderby: String): OrderBy = {
    new OrderBy(orderby)
  }

  protected def typeis(t: String): TypeSetter = {
    new TypeSetter(t)
  }

  protected final def bind[T: ClassTag]()(implicit manifest: Manifest[T], ttag: ru.TypeTag[T]): EntityHolder[T] = {
    bind(manifest.runtimeClass.asInstanceOf[Class[T]], null, ttag)
  }

  protected final def bind[T: ClassTag](entityName: String)(implicit manifest: Manifest[T], ttag: ru.TypeTag[T]): EntityHolder[T] = {
    bind(manifest.runtimeClass.asInstanceOf[Class[T]], entityName, ttag)
  }

  private def bind[T](cls: Class[T], entityName: String, ttag: ru.TypeTag[T]): EntityHolder[T] = {
    val entity = binder.autobind(cls, entityName, ttag.tpe)
    //find superclass's id generator
    var superCls: Class[_] = cls.getSuperclass
    while (null != superCls && superCls != classOf[Object]) {
      if (entities.contains(superCls.getName)) {
        entities(superCls.getName).idGenerator match {
          case Some(idg) =>
            entity.idGenerator = Some(idg); superCls = classOf[Object]
          case None =>
        }
      }
      superCls = superCls.getSuperclass
    }
    if (entity.idGenerator.isEmpty) this.defaultIdGenerator foreach { a => entity.idGenerator = Some(new IdGenerator(a)) }
    binder.addEntity(entity)
    val holder = new EntityHolder(entity, binder, cls, this)
    currentHolder = holder
    entities.put(entity.entityName, entity)
    holder
  }

  protected final def defaultIdGenerator(strategy: String): Unit = {
    defaultIdGenerator = Some(strategy)
  }

  protected final def cache(region: String): CacheHolder = {
    new CacheHolder(binder, region, cacheConfig.usage)
  }

  protected final def cache(): CacheHolder = {
    new CacheHolder(binder, cacheConfig.region, cacheConfig.usage)
  }

  protected final def all: Entities = {
    val newEntities = Collections.newMap[String, Entity]
    new Entities(newEntities ++ entities, cacheConfig)
  }

  protected final def collection[T](properties: String*)(implicit manifest: Manifest[T]): List[Collection] = {
    import scala.collection.mutable
    val definitions = new mutable.ListBuffer[Collection]
    val clazz = manifest.runtimeClass
    for (property <- properties) {
      definitions += new Collection(clazz, property)
    }
    definitions.toList
  }

  protected final def defaultCache(region: String, usage: String) {
    cacheConfig.region = region
    cacheConfig.usage = usage
  }

  final def configure(binder: Binder): Unit = {
    this.binder = binder
  }

  def typedef(name: String, clazz: String, params: Map[String, String] = Map.empty): Unit = {
    binder.addType(name, clazz, params)
  }

  def typedef(forClass: Class[_], clazz: String): Unit = {
    binder.addType(forClass.getName, clazz, Map.empty)
  }

  def typedef(forClass: Class[_], clazz: String, params: Map[String, String]): Unit = {
    binder.addType(forClass.getName, clazz, params)
  }

  def registerTypes(): Unit = {}

  final def clear(): Unit = {
    entities.clear()
  }
}

