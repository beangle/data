/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.jsonapi

import org.beangle.commons.collection.{Collections, Properties}
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.commons.lang.reflect.TypeInfo.IterableType
import org.beangle.commons.text.inflector.en.EnNounPluralizer
import org.beangle.data.model.Entity
import org.beangle.data.orm.Jpas

import java.time.*
import scala.collection.mutable

/**
 * Create JSON api
 *
 * @see https://jsonapi.org/format/
 */
@beta
object JsonAPI {

  def newJson(data: Resource)(using context: Context): Json = {
    val json = new Json
    json.put("data", data)
    val included = context.includedSeq
    if included.nonEmpty then
      json.put("included", included)
    json
  }

  def newJson(datas: Iterable[Resource])(using context: Context): Json = {
    val json = new Json
    json.put("data", datas)
    val included = context.includedSeq
    if included.nonEmpty then
      json.put("included", included)
    json
  }

  def context(params: collection.Map[String, Any]): Context = {
    val ctx = new Context()
    params foreach { case (key, value) =>
      if key.startsWith("fields[") then
        val typ = Strings.substringBetween(key, "fields[", "]")
        ctx.filters.include(typ, Strings.split(value.toString))
      else if key == "include" then
        ctx.includes ++= Strings.split(value.toString)
    }
    ctx
  }

  def create(entity: Entity[_], path: String)(using context: Context): Resource = {
    val clazz = Jpas.entityClass(entity)
    val entityType = typeName(clazz)
    val id = entity.id.toString
    if Strings.isEmpty(path) then context.primaryResourceTypes.addOne(entityType)
    val datas = context.includedResources.getOrElseUpdate(entityType, Collections.newMap)
    datas.get(id) match {
      case Some(r) => r.asInstanceOf[Resource]
      case None =>
        datas.put(id, new Resource(id, entityType, path))
        val m = new Resource(id, entityType, path)
        val ginfo = BeanInfos.get(clazz)
        val filter = context.filters.getFilter(clazz)
        ginfo.properties foreach { p =>
          if (p._2.isTransient) filter.transients += p._1
          if (p._2.getter.nonEmpty && filter.isIncluded(p._1)) {
            val pName = p._1
            val pValue = p._2.getter.get.invoke(entity)
            val typeInfo = p._2.typeinfo
            if typeInfo.isIterable then
              val elemType = typeInfo.asInstanceOf[IterableType].elementType
              if classOf[Entity[_]].isAssignableFrom(elemType.clazz) then
                m.refs(pName, pValue.asInstanceOf[Iterable[Entity[_]]])
              else
                m.attr(pName, pValue)
            else if typeInfo.isOptional then
              pValue.asInstanceOf[Option[_]] foreach { inner =>
                m.field(pName, inner)
              }
            else
              m.field(pName, pValue)
          }
        }
        if m.attributes.isEmpty then m.attributes = null
        if m.relationships.isEmpty then m.relationships = null
        datas.put(id, m)
        m
    }
  }

  def typeName(clazz: Class[_]): String = {
    EnNounPluralizer.pluralize(Strings.unCamel(clazz.getSimpleName))
  }

  def extractOption(item: Any): Any = {
    item match {
      case null => null
      case o: Option[Any] => o.orNull.asInstanceOf[AnyRef]
      case _ => item
    }
  }

  class Json extends org.beangle.commons.collection.Properties {
  }

  class Filter(val includes: Set[String], val excludes: Set[String]) {

    val transients: mutable.Set[String] = Collections.newSet[String]

    def isIncluded(name: String): Boolean = {
      if excludes.contains(name) then false
      else includes.contains(name) || (includes.contains("*") && !transients.contains(name))
    }

    def merge(newIncludes: collection.Set[String], newExcludes: collection.Set[String]): Filter = {
      var includeSum = includes ++ newIncludes
      val excludeSum = excludes ++ newExcludes
      if includeSum.contains("*") && includeSum.size > 1 then
        includeSum -= "*"
      new Filter(includeSum, excludeSum)
    }
  }

  class Filters {
    private val filters = Collections.newMap[String, Filter]

    def getFilter(clazz: Class[_]): Filter = {
      filters.getOrElse(typeName(clazz), createDefault(clazz))
    }

    private def createDefault(clazz: Class[_]): Filter = {
      val includeNames = Collections.newSet[String]
      val excludeNames = Collections.newSet[String]
      getSupers(clazz) foreach { i =>
        filters.get(typeName(i)) foreach { f =>
          includeNames ++= f.includes
          excludeNames ++= f.excludes
        }
      }
      val defaults = new Filter(Set("*"), Set("id"))
      val filter = defaults.merge(includeNames, excludeNames)
      filters.put(typeName(clazz), filter)
      filter
    }

    private def getSupers(clazz: Class[_]): Iterable[Class[_]] = {
      val supers = Collections.newBuffer[Class[_]]
      supers ++= clazz.getInterfaces
      var superClazz = clazz.getSuperclass
      while (null != superClazz && superClazz != classOf[Any]) {
        supers += superClazz
        superClazz = superClazz.getSuperclass
      }
      supers += classOf[Any]
      supers
    }

    def exclude(clazz: Class[_], names: String*): Unit = {
      val typ = typeName(clazz)
      val filter = filters.getOrElse(typ, createDefault(clazz))
      filters.put(typ, filter.merge(Set.empty, filter.excludes ++ names))
    }

    def exclude(typeName: String, names: Iterable[String]): Unit = {
      val filter = filters.getOrElseUpdate(typeName, new Filter(Set("*"), Set("id")))
      filters.put(typeName, filter.merge(Set.empty, names.toSet))
    }

    def include(clazz: Class[_], names: String*): Unit = {
      val typ = typeName(clazz)
      val filter = filters.getOrElse(typ, createDefault(clazz))
      filters.put(typ, filter.merge(filter.includes ++ names, Set.empty))
    }

    def include(typeName: String, names: Iterable[String]): Unit = {
      val filter = filters.getOrElseUpdate(typeName, new Filter(Set("*"), Set("id")))
      filters.put(typeName, filter.merge(names.toSet, Set.empty))
    }
  }

  class Context {

    val filters: Filters = new Filters

    private[jsonapi] val includes = Collections.newSet[String]

    private[jsonapi] val primaryResourceTypes = Collections.newSet[String]
    //type -> {id:resources}*
    private[jsonapi] val includedResources = Collections.newMap[String, mutable.Map[String, Any]]

    def shouldInclude(path: String): Boolean = {
      if includes.isEmpty then true else includes.contains(path)
    }

    def includedSeq: Iterable[Any] = {
      val included = Collections.newBuffer[Any]
      if this.includedResources.nonEmpty then
        this.includedResources foreach { case (typ, valueMap) =>
          if !this.primaryResourceTypes.contains(typ) then
            valueMap foreach { case (_, value) => included += value }
        }
      included
    }

    def mkJson(entities: Iterable[Entity[_]], properties: String*): Json = {
      given context: Context = this

      if entities.nonEmpty then
        val first = entities.head
        this.filters.include(Jpas.entityClass(first), properties: _*)
      end if
      val resources = entities.map { g => JsonAPI.create(g, "") }
      JsonAPI.newJson(resources)
    }

  }

  /** Resource Object
   *
   * @param id   resource identifiers
   * @param type typeName of resource
   */
  class Resource(val id: String, val `type`: String, path: String) {
    var attributes = new Properties
    var relationships = new Properties
    var links: Links = _

    def linkSelf(url: String): Resource = {
      links = new Links(url)
      this
    }

    def field(pName: String, value: Any)(using context: JsonAPI.Context): Resource = {
      value match {
        case e: Entity[_] => this.ref(pName, e)
        case _ => this.attr(pName, value)
      }
      this
    }

    def attr(name: String, value: Any): Resource = {
      if (null == attributes) attributes = new Properties
      attributes.put(name, value)
      this
    }

    def ref(name: String, value: Entity[_])(using context: JsonAPI.Context): Resource = {
      val pJson = new Json
      val typ = typeName(Jpas.entityClass(value))
      val id = value.id.toString
      val refer = Identifier(id, typ)
      pJson.put("data", refer)
      if (null == relationships) relationships = new Properties
      this.relationships.put(name, pJson)
      val refPath = pathFor(name)
      if context.shouldInclude(refPath) then
        val refDatas = context.includedResources.getOrElseUpdate(typ, Collections.newMap)
        if !refDatas.contains(id) then JsonAPI.create(value, refPath)

      this
    }

    def refs(name: String, pValues: Iterable[Entity[_]])(using context: JsonAPI.Context): Resource = {
      if pValues.nonEmpty then
        val pJson = new Json
        val typ = typeName(Jpas.entityClass(pValues.head))
        val ids = pValues.map(x => Identifier(x.id.toString, typ))
        pJson.put("data", ids)
        if (null == relationships) relationships = new Properties
        this.relationships.put(name, pJson)

        val refsPath = pathFor(name)
        if context.shouldInclude(refsPath) then
          pValues foreach { pValue =>
            val refDatas = context.includedResources.getOrElseUpdate(typ, Collections.newMap)
            val id = pValue.id.toString
            if !refDatas.contains(id) then
              JsonAPI.create(pValue, refsPath)
          }
      end if
      this
    }

    def pathFor(name: String): String = {
      if Strings.isEmpty(path) then name
      else s"$path.$name"
    }
  }

  /** Resource Identifier Object
   */
  object Identifier {
    def apply(id: String, typeName: String): Properties = {
      new Properties("id" -> id, "type" -> typeName)
    }
  }

  class Links(val self: String) {
    var related: Option[String] = None
  }

}
