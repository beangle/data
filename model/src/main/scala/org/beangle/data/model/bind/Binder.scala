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
import scala.collection.mutable.Buffer

import org.beangle.commons.collection.Collections

object Binder {
  final class Collection(val clazz: Class[_], val property: String) {
    var cacheRegion: String = _
    var cacheUsage: String = _

    def cache(region: String, usage: String): this.type = {
      this.cacheRegion = region;
      this.cacheUsage = usage;
      return this;
    }
  }

  final class IdGenerator(var generator: String) {
    val params = Collections.newMap[String, String]
    var clazz: Option[String] = None
    var nullValue: Option[String] = None
  }

  final class Entity(val clazz: Class[_], val entityName: String) {
    var cacheUsage: String = _
    var cacheRegion: String = _
    var isLazy: Boolean = true
    var proxy: String = _
    var schema: String = _
    var table: String = _
    var isAbstract: Boolean = _

    var idGenerator: IdGenerator = new IdGenerator("native")

    val properties = Collections.newMap[String, Property]

    def this(clazz: Class[_]) {
      this(clazz, Jpas.findEntityName(clazz))
    }

    def cache(region: String, usage: String): this.type = {
      this.cacheRegion = region;
      this.cacheUsage = usage;
      return this;
    }

    def getColumns(property: String): Seq[Column] = {
      properties(property).columns
    }

    def getProperty(property: String): Property = {
      properties(property)
    }

    override def hashCode: Int = clazz.hashCode()

    override def equals(obj: Any): Boolean = clazz.equals(obj)
  }

  trait ColumnHolder {
    var columns: Buffer[Column] = Buffer.empty[Column]
  }
  trait TypeNameHolder {
    var typeName: Option[String] = None
  }

  final class Column(var name: String) {
    var length: Option[Int] = None
    var scale: Option[Int] = None
    var precision: Option[Int] = None
    var nullable: Boolean = true
    var unique: Boolean = false

    var defaultValue: Option[String] = None
    var sqlType: Option[String] = None
  }

  class Property(val name: String, val propertyType: Class[_]) extends ColumnHolder {
    var access: Option[String] = None
    var cascade: Option[String] = None

    var updateable: Boolean = true
    var insertable: Boolean = true
    var optimisticLocked: Boolean = true
    var lazyed: Boolean = false

    var generated: Option[String] = None

  }

  class ScalarProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with TypeNameHolder {

  }

  class IdProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with TypeNameHolder {
  }

  class ManyToOneProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with Fetchable {
    var targetEntity: String = _
  }

  class ComponentProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with Component {
    this.clazz = Some(propertyType.getName)
    var unique: Boolean = false
  }

  trait Fetchable {
    var fetch: Option[String] = None
  }

  class CollectionProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with Fetchable with TypeNameHolder {
    var inverse: Boolean = false
    var orderBy: Option[String] = None
    var where: Option[String] = None
    var batchSize: Option[Int] = None

    var index: Option[Index] = None
    var key: Option[Key] = None
    var element: Option[Element] = None

    var table: Option[String] = None
    var schema: Option[String] = None
    var subselect: Option[String] = None
    var sort: Option[String] = None
  }

  class SeqProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType) {

  }

  class SetProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType)
  class MapProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType) {
    var mapKey: Key = _
  }

  class Key

  class SimpleKey(column: Column) extends Key with ColumnHolder with TypeNameHolder {
    if (null != column) columns += column
  }

  class CompositeKey extends Key with Component {

  }

  class ManyToOneKey extends Key with ColumnHolder {
    var entityName: String = _
  }

  class Index(column: Column) extends ColumnHolder with TypeNameHolder {
    columns += column
  }

  class Element {

  }

  class ManyToManyElement(var entityName: String, column: Column) extends Element with ColumnHolder {
    columns += column
  }

  class OneToManyElement extends Element {
    var entityName: String = _

  }

  trait Component {
    var clazz: Option[String] = None
    val properties = Collections.newMap[String, Property]
  }

  class CompositeElement extends Element with Component {

  }

  class SimpleElement(column: Column) extends Element with ColumnHolder with TypeNameHolder {
    if (null != column) columns += column
  }

  final class CacheConfig(var region: String = null, var usage: String = null) {
  }
}
/**
 * @author chaostone
 * @since 3.1
 */
final class Binder {

  import Binder._
  /**
   * Classname -> Entity
   */
  val entityMap = new mutable.HashMap[String, Entity]

  /**
   * Classname.property -> Collection
   */
  val collectMap = new mutable.HashMap[String, Collection]

  val cache = new CacheConfig();

  def entities: Iterable[Entity] = entityMap.values

  def collections: Iterable[Collection] = collectMap.values

  def getEntity(clazz: Class[_]): Entity = entityMap(clazz.getName)

  def addEntity(definition: Entity): this.type = {
    entityMap.put(definition.clazz.getName(), definition)
    this
  }

  def addCollection(definition: Collection): this.type = {
    collectMap.put(definition.clazz.getName() + definition.property, definition)
    return this
  }
}
