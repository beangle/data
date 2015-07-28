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
import org.beangle.commons.lang.reflect.BeanManifest
import javassist.CtMethod
import javassist.ClassPool
import javassist.CtConstructor
import javassist.compiler.Javac
import org.beangle.commons.lang.ClassLoaders
import javassist.LoaderClassPath
import javassist.CtField
import org.beangle.commons.lang.Primitives

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

  /**
   * genderator shortname or qualified name
   */
  final class IdGenerator(var generator: String) {
    val params = Collections.newMap[String, String]
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

    var idGenerator: Option[IdGenerator] = None

    val properties = Collections.newMap[String, Property]

    def this(clazz: Class[_]) {
      this(clazz, Jpas.findEntityName(clazz))
    }

    def cache(region: String, usage: String): this.type = {
      this.cacheRegion = region
      this.cacheUsage = usage
      this
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

  final class Column(var name: String) extends Cloneable {
    var length: Option[Int] = None
    var scale: Option[Int] = None
    var precision: Option[Int] = None
    var nullable: Boolean = true
    var unique: Boolean = false

    var defaultValue: Option[String] = None
    var sqlType: Option[String] = None

    override def clone: this.type = {
      super.clone().asInstanceOf[this.type]
    }
  }

  class Property(val name: String, val propertyType: Class[_]) extends ColumnHolder with Cloneable {
    var access: Option[String] = None
    var cascade: Option[String] = None

    var updateable: Boolean = true
    var insertable: Boolean = true
    var optimisticLocked: Boolean = true
    var lazyed: Boolean = false

    var generated: Option[String] = None

    override def clone: this.type = {
      val cloned = super.clone().asInstanceOf[this.type]
      val old = cloned.columns
      cloned.columns = Buffer.empty[Column]
      old foreach { c =>
        cloned.columns += c.clone()
      }
      cloned
    }
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

  class ToManyElement(var entityName: String) extends Element with ColumnHolder {
    var one2many = true
    def this(entityName: String, column: Column) {
      this(entityName)
      this.one2many = false
      columns += column
    }

    def many2many: Boolean = {
      !one2many
    }
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

  trait ModelProxy {
    def lastAccessed(): java.util.Set[String]
  }
}
/**
 * @author chaostone
 * @since 3.1
 */
final class Binder {

  import Binder._
  val pool = new ClassPool(true)
  pool.appendClassPath(new LoaderClassPath(ClassLoaders.defaultClassLoader))

  var defaultIdGenerator: Option[String] = None
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

  def generateProxy(clazz: Class[_]): ModelProxy = {
    val proxyClassName = clazz.getSimpleName + "_proxy"
    val fullClassName = clazz.getName + "_proxy"
    try {
      val proxyCls = ClassLoaders.loadClass(fullClassName)
      return proxyCls.getConstructor().newInstance().asInstanceOf[ModelProxy]
    } catch {
      case e: Exception =>
    }
    val cct = pool.makeClass(fullClassName)
    if (clazz.isInterface()) cct.addInterface(pool.makeClass(clazz.getName))
    else cct.setSuperclass(pool.makeClass(clazz.getName))
    cct.addInterface(pool.get(classOf[ModelProxy].getName))
    val javac = new Javac(cct)
    cct.addField(javac.compile("public java.util.Set _lastAccessed;").asInstanceOf[CtField])
    val manifest = BeanManifest.get(clazz)
    manifest.getters foreach {
      case (name, m) =>
        var value = String.valueOf(Primitives.default(m.returnType))
        if (m.returnType == classOf[Long]) value += "l"
        else if (m.returnType == classOf[Double]) value += "d"
        val body = s"public ${m.returnType.getName} ${m.method.getName}() { return $value;}"
        val ctmod = javac.compile(body).asInstanceOf[CtMethod]
        ctmod.setBody("{_lastAccessed.add(\"" + name + "\");return " + value + ";}")
        cct.addMethod(ctmod)
    }
    val ctor = javac.compile("public " + proxyClassName + "(){}").asInstanceOf[CtConstructor]
    ctor.setBody("_lastAccessed = new java.util.HashSet();")
    cct.addConstructor(ctor)

    val ctmod = javac.compile("public java.util.Set lastAccessed() { return null;}").asInstanceOf[CtMethod]
    ctmod.setBody("{return _lastAccessed;}")
    cct.addMethod(ctmod)
    //    cct.debugWriteFile("/tmp/handlers")
    val maked = cct.toClass()
    cct.detach()
    maked.getConstructor().newInstance().asInstanceOf[ModelProxy]
  }

}
