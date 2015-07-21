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

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.reflect.runtime.{ universe => ru }
import scala.reflect.runtime.universe.TypeTag
import org.beangle.commons.lang.{ ClassLoaders, Primitives, Strings }
import org.beangle.commons.lang.reflect.{ BeanManifest, Getter }
import org.beangle.data.model.{ Entity => MEntity, Component => MComponent }
import org.beangle.data.model.bind.Binder._

abstract class AbstractPersistModule {

  private var binder: Binder = _

  protected def binding(): Unit

  protected final def add[T: ClassTag](cls: Class[T])(implicit ttag: TypeTag[T]): EntityHolder = {
    val entity = new Entity(cls)
    autobind(entity, ttag.tpe)
    binder.addEntity(entity)
    new EntityHolder(binder, cls)
  }

  /**
   * support
   * <li> buildin primary type will be not null
   */
  private def autobind(entity: Entity, tye: ru.Type): Unit = {
    val cls = entity.clazz
    if (!cls.isAnnotationPresent(classOf[javax.persistence.Entity])) {
      val manifest = BeanManifest.get(cls)
      val writes = manifest.getWritableProperties()
      manifest.getters foreach {
        case (name, method) =>
          if (writes.contains(name)) {
            val p =
              if (name == "id") bindId(name, method, tye)
              else if (classOf[MEntity[_]].isAssignableFrom(method.returnType)) {
                bindManyToOne(name, method, tye)
              } else if (classOf[scala.collection.mutable.Seq[_]].isAssignableFrom(method.returnType)) {
                bindSeq(name, method, entity, tye)
              } else if (classOf[scala.collection.mutable.Set[_]].isAssignableFrom(method.returnType)) {
                bindSet(name, method, entity, tye)
              } else if (classOf[scala.collection.mutable.Map[_, _]].isAssignableFrom(method.returnType)) {
                bindMap(name, method, entity, tye)
              } else if (classOf[MComponent].isAssignableFrom(method.returnType)) {
                bindComponent(name, method, entity, tye)
              } else {
                bindScalar(name, method, tye)
              }
            entity.properties += (name -> p)
          }
      }
    }
  }

  private def bindComponent(name: String, method: Getter, entity: Entity, tye: ru.Type): ComponentProperty = {
    val cp = new ComponentProperty(name, method.returnType)
    val manifest = BeanManifest.get(method.returnType)
    val writes = manifest.getWritableProperties()
    val ctype = tye.member(ru.TermName(name)).asMethod.returnType
    manifest.getters foreach {
      case (name, method) =>
        if (writes.contains(name)) {
          val p =
            if (classOf[MEntity[_]].isAssignableFrom(method.returnType)) {
              bindManyToOne(name, method, ctype)
            } else if (classOf[scala.collection.mutable.Seq[_]].isAssignableFrom(method.returnType)) {
              bindSeq(name, method, entity, ctype)
            } else if (classOf[scala.collection.mutable.Set[_]].isAssignableFrom(method.returnType)) {
              bindSet(name, method, entity, ctype)
            } else if (classOf[scala.collection.mutable.Map[_, _]].isAssignableFrom(method.returnType)) {
              bindMap(name, method, entity, tye)
            } else if (classOf[MComponent].isAssignableFrom(method.returnType)) {
              bindComponent(name, method, entity, ctype)
            } else {
              bindScalar(name, method, ctype)
            }
          cp.properties += (name -> p)
        }
    }
    cp
  }

  private def bindMap(name: String, method: Getter, entity: Entity, tye: ru.Type): MapProperty = {
    val p = new MapProperty(name, method.returnType)
    val typeSignature = tye.member(ru.TermName(name)).typeSignature.toString
    val kvtype = Strings.substringBetween(typeSignature, "[", "]")

    val mapKeyType = Strings.substringBefore(kvtype, ",").trim
    val mapEleType = Strings.substringAfter(kvtype, ",").trim

    val mapKey = new SimpleKey(new Column("key"))
    mapKey.typeName = Some(if (mapKeyType.contains(".")) mapKeyType else "java.lang." + mapKeyType)

    val mapElem = new SimpleElement(new Column("value"))
    mapElem.typeName = Some(if (mapEleType.contains(".")) mapKeyType else "java.lang." + mapEleType)

    //val m2m = new ManyToManyElement(entityName, new Column(columnName(entityName, true)))

    val key = new SimpleKey(new Column(columnName(entity.entityName, true)))
    p.key = Some(key)
    p.mapKey = mapKey
    p.element = Some(mapElem)
    p
  }

  private def bindSeq(name: String, method: Getter, entity: Entity, tye: ru.Type): SeqProperty = {
    val p = new SeqProperty(name, method.returnType)
    val typeSignature = tye.member(ru.TermName(name)).typeSignature.toString()
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    val m2m = new ManyToManyElement(entityName, new Column(columnName(entityName, true)))
    val key = new SimpleKey(new Column(columnName(entity.entityName, true)))

    val idx = new Index(new Column("idx"))
    p.element = Some(m2m)
    p.key = Some(key)
    p.index = Some(idx)
    p
  }

  private def bindSet(name: String, method: Getter, entity: Entity, tye: ru.Type): SetProperty = {
    val p = new SetProperty(name, method.returnType)
    val typeSignature = tye.member(ru.TermName(name)).typeSignature.toString()
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    val m2m = new ManyToManyElement(entityName, new Column(columnName(entityName, true)))
    val key = new SimpleKey(new Column(columnName(entity.entityName, true)))

    p.element = Some(m2m)
    p.key = Some(key)
    p
  }

  private def bindId(name: String, method: Getter, tye: ru.Type): IdProperty = {
    val p = new IdProperty(name, method.returnType)
    val column = new Column(columnName(name))
    if (Primitives.isWrapperType(method.returnType)) {
      column.nullable = false
    }
    p.columns += column
    p
  }

  private def bindScalar(name: String, method: Getter, tye: ru.Type): ScalarProperty = {
    val p = new ScalarProperty(name, method.returnType)
    val column = new Column(columnName(name))
    if (method.returnType == classOf[Option[_]]) {
      val a = tye.member(ru.TermName(name)).typeSignature
      val innerType = a.resultType.typeArgs.head.toString
      val innerClass = ClassLoaders.loadClass(if (innerType.contains(".")) innerType else "java.lang." + innerType)
      val primitiveClass = Primitives.unwrap(innerClass)
      p.typeName = Some(primitiveClass.getName + "?")
    } else if (Primitives.isWrapperType(method.returnType)) {
      column.nullable = false
    }
    p.columns += column
    p
  }

  private def bindManyToOne(name: String, method: Getter, tye: ru.Type): ManyToOneProperty = {
    val p = new ManyToOneProperty(name, method.returnType)
    val column = new Column(columnName(name, true))
    p.targetEntity = method.returnType.getName
    p.columns += column
    p
  }
  private def columnName(propertyName: String, key: Boolean = false): String = {
    val lastDot = propertyName.lastIndexOf(".")
    val columnName = if (lastDot == -1) s"@${propertyName}" else "@" + propertyName.substring(lastDot + 1)
    if (key) columnName + "Id" else columnName
  }

  protected final def cache(region: String): CacheHolder = {
    new CacheHolder(binder).cache(region).usage(binder.cache.usage);
  }

  protected final def cache(): CacheHolder = {
    new CacheHolder(binder).cache(binder.cache.region).usage(binder.cache.usage);
  }

  protected final def collection(clazz: Class[_], properties: String*): List[Collection] = {
    import scala.collection.mutable
    val definitions = new mutable.ListBuffer[Collection]
    for (property <- properties) {
      definitions += new Collection(clazz, property)
    }
    definitions.toList
  }

  protected final def defaultCache(region: String, usage: String) {
    binder.cache.region = region;
    binder.cache.usage = usage;
  }

  final def getConfig(): Binder = {
    binder = new Binder()
    binding()
    binder
  }

  final class CacheHolder(val binder: Binder) {
    var cacheUsage: String = _
    var cacheRegion: String = _

    def add(first: List[Collection], definitionLists: List[Collection]*): this.type = {
      for (definition <- first) {
        binder.addCollection(definition.cache(cacheRegion, cacheUsage));
      }
      for (definitions <- definitionLists) {
        for (definition <- definitions) {
          binder.addCollection(definition.cache(cacheRegion, cacheUsage));
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

  final class EntityHolder(val binder: Binder, val classes: Class[_]*) {

    def cacheable(): EntityHolder = {
      for (clazz <- classes) {
        binder.getEntity(clazz).cache(binder.cache.region, binder.cache.usage);
      }
      this
    }

    def cache(region: String): EntityHolder = {
      for (clazz <- classes) {
        binder.getEntity(clazz).cacheRegion = region
      }
      this
    }

    def usage(usage: String): EntityHolder = {
      for (clazz <- classes) {
        binder.getEntity(clazz).cacheUsage = usage
      }
      this
    }

  }
}
