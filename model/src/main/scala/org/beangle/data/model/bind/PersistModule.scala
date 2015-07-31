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
import scala.reflect.runtime.{universe => ru}

import org.beangle.commons.lang.annotation.beta
import org.beangle.data.model.bind.Binder.{Collection, CollectionProperty, Column, Entity, IdGenerator, ModelProxy, Property, SimpleKey, ToManyElement}
import org.beangle.data.model.{ Entity => MEntity, Component => MComponent }

object PersistModule {

  class Expression(val holder: EntityHolder[_]) {
    def is(declarations: Declaration*): Expression = {
      val lasts = holder.proxy.lastAccessed()
      import collection.JavaConversions.asScalaSet
      for (property <- lasts; declaration <- declarations) {
        val p =holder.entity.getProperty(property)
        p.mergeable=false
        declaration(holder, p)
      }
      lasts.clear()
      this
    }
  }

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

  final class EntityHolder[T](val entity: Entity, val binder: Binder, val clazz: Class[T]) {

    var proxy: ModelProxy = _

    def cacheable(): this.type = {
      entity.cache(binder.cache.region, binder.cache.usage)
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

    def on(declarations: T => Seq[Expression])(implicit manifest: Manifest[T]): this.type = {
      generateProxy()
      declarations(proxy.asInstanceOf[T])
      this
    }

    def generator(strategy: String): this.type = {
      entity.idGenerator = Some(new IdGenerator(strategy))
      this
    }

    def generateProxy(): Unit = {
      if (null != proxy) return
      proxy = binder.generateProxy(clazz)
    }
  }

  final class CacheHolder(val binder: Binder) {
    var cacheUsage: String = _
    var cacheRegion: String = _

    def add(first: List[Collection], definitionLists: List[Collection]*): this.type = {
      for (definition <- first) {
        binder.addCollection(definition.cache(cacheRegion, cacheUsage))
      }
      for (definitions <- definitionLists) {
        for (definition <- definitions) {
          binder.addCollection(definition.cache(cacheRegion, cacheUsage))
        }
      }
      this
    }

    def add(first: Class[_ <: MEntity[_]], classes: Class[_ <: MEntity[_]]*): this.type = {
      binder.getEntity(first).cache(cacheRegion, cacheUsage)
      for (clazz <- classes)
        binder.getEntity(clazz).cache(cacheRegion, cacheUsage)
      this
    }

    def usage(cacheUsage: String): this.type = {
      this.cacheUsage = cacheUsage
      this
    }

    def cache(cacheRegion: String): this.type = {
      this.cacheRegion = cacheRegion
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
abstract class PersistModule {

  import PersistModule._
  var currentHolder: EntityHolder[_] = _
  var defaultIdGenerator: Option[String] = None

  import scala.language.implicitConversions
  implicit def any2Expression(i: Any): Expression = {
    new Expression(currentHolder)
  }

  private var binder: Binder = _

  protected def binding(): Unit

  protected def declare[B](a: B*): Seq[B] = {
    a
  }

  protected def notnull = new NotNull

  protected def unique = new Unique

  protected def length(len: Int) = new Length(len)

  protected def cacheable: Cache = {
    new Cache(new CacheHolder(binder).cache(binder.cache.region).usage(binder.cache.usage))
  }

  protected def cacheable(region: String, usage: String): Cache = {
    new Cache(new CacheHolder(binder).cache(region).usage(usage))
  }

  protected def one2many(mappedBy: String): One2Many = {
    new One2Many(null, mappedBy)
  }

  protected def orderby(orderby: String): OrderBy = {
    new OrderBy(orderby)
  }

  protected final def bind[T: ClassTag]()(implicit manifest: Manifest[T], ttag: ru.TypeTag[T]): EntityHolder[T] = {
    val cls = manifest.runtimeClass.asInstanceOf[Class[T]]
    val entity= binder.autobind(cls, ttag.tpe)
    this.defaultIdGenerator foreach { a => entity.idGenerator = Some(new IdGenerator(a)) }
    binder.addEntity(entity)
    val holder = new EntityHolder(entity, binder, cls)
    currentHolder = holder
    holder
  }

  protected final def defaultIdGenerator(strategy: String): Unit = {
    defaultIdGenerator = Some(strategy)
  }

  protected final def cache(region: String): CacheHolder = {
    new CacheHolder(binder).cache(region).usage(binder.cache.usage)
  }

  protected final def cache(): CacheHolder = {
    new CacheHolder(binder).cache(binder.cache.region).usage(binder.cache.usage)
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
    binder.cache.region = region
    binder.cache.usage = usage
  }

  final def configure(binder: Binder): Unit = {
    this.binder = binder
    binding()
  }
}

