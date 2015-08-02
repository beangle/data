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

import java.lang.reflect.{ Method, Modifier }

import scala.collection.mutable
import scala.collection.mutable.Buffer
import scala.reflect.runtime.{ universe => ru }

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.{ ClassLoaders, Primitives, Strings }
import org.beangle.commons.lang.reflect.{ BeanManifest, MethodInfo }
import org.beangle.data.model.{ Component => MComponent, Entity => MEntity }

import javassist.{ ClassPool, CtConstructor, CtField, CtMethod, LoaderClassPath }
import javassist.compiler.Javac

object Binder {
  final class Collection(val clazz: Class[_], val property: String) {
    var cacheRegion: String = _
    var cacheUsage: String = _

    def cache(region: String, usage: String): this.type = {
      this.cacheRegion = region
      this.cacheUsage = usage
      this
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
    var mergeable: Boolean = true

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

  final class ScalarProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with TypeNameHolder {

  }

  final class IdProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with TypeNameHolder {
  }

  final class ManyToOneProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with Fetchable {
    var targetEntity: String = _
  }

  final class ComponentProperty(name: String, propertyType: Class[_]) extends Property(name, propertyType) with Component {
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

  final class SeqProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType) {

  }

  final class SetProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType)

  final class MapProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType) {
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

  trait ModelProxy {
    def lastAccessed(): java.util.Set[String]
  }

  def columnName(propertyName: String, key: Boolean = false): String = {
    val lastDot = propertyName.lastIndexOf(".")
    val columnName = if (lastDot == -1) s"@${propertyName}" else "@" + propertyName.substring(lastDot + 1)
    if (key) columnName + "Id" else columnName
  }

  class TypeDef(val clazz: String, val params: Map[String, String])
}
/**
 * @author chaostone
 * @since 3.1
 */
final class Binder {

  import Binder._
  private var pool = new ClassPool(true)
  pool.appendClassPath(new LoaderClassPath(ClassLoaders.defaultClassLoader))

  /**
   * all type mappings(clazz -> Entity)
   */
  val mappings = new mutable.HashMap[Class[_], Entity]

  /**
   * custome types
   */
  val types = new mutable.HashMap[String, TypeDef]

  /**
   * Classname.property -> Collection
   */
  val collectMap = new mutable.HashMap[String, Collection]

  /**
   * Only entities
   */
  val entities = Collections.newSet[Entity]

  def collections: Iterable[Collection] = collectMap.values

  def getEntity(clazz: Class[_]): Entity = mappings(clazz)

  def addEntity(entity: Entity): this.type = {
    val cls = entity.clazz
    mappings.put(cls, entity)
    if (!cls.isInterface() && !Modifier.isAbstract(cls.getModifiers)) entities += entity
    this
  }

  def addCollection(definition: Collection): this.type = {
    collectMap.put(definition.clazz.getName() + definition.property, definition)
    this
  }

  def addType(name: String, clazz: String, params: Map[String, String]): Unit = {
    types.put(name, new TypeDef(clazz, params))
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
    manifest.properties foreach {
      case (name, p) =>
        if (p.readable) {
          val value = Primitives.defaultLiteral(p.clazz)
          val getter = p.getter.get
          val body = s"public ${p.clazz.getName} ${getter.getName}() { return $value;}"
          val ctmod = javac.compile(body).asInstanceOf[CtMethod]
          ctmod.setBody("{_lastAccessed.add(\"" + name + "\");return " + value + ";}")
          cct.addMethod(ctmod)
        }
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

  def autobind(): Unit = {
    mappings.keys.toList.sortWith { (a, b) => a.isAssignableFrom(b) } foreach (cls => merge(mappings(cls)))
  }

  def clear(): Unit = {
    this.entities.clear()
    this.mappings.clear()
    this.collectMap.clear()
    this.types.clear()
    this.pool = null
  }

  def autobind(cls: Class[_], tpe: ru.Type): Entity = {
    val entity = new Entity(cls)
    if (cls.isAnnotationPresent(classOf[javax.persistence.Entity])) return entity;
    val manifest = BeanManifest.get(entity.clazz, tpe)
    manifest.readables foreach {
      case (name, prop) =>
        if (prop.writable) {
          val returnType = prop.clazz
          val p =
            if (name == "id") {
              bindId(name, returnType, tpe)
            } else if (classOf[MEntity[_]].isAssignableFrom(returnType)) {
              bindManyToOne(name, returnType, tpe)
            } else if (classOf[scala.collection.mutable.Seq[_]].isAssignableFrom(returnType)) {
              bindSeq(name, returnType, entity, tpe)
            } else if (classOf[scala.collection.mutable.Set[_]].isAssignableFrom(returnType)) {
              bindSet(name, returnType, entity, tpe)
            } else if (classOf[scala.collection.mutable.Map[_, _]].isAssignableFrom(returnType)) {
              bindMap(name, returnType, entity, tpe)
            } else if (classOf[MComponent].isAssignableFrom(returnType)) {
              bindComponent(name, returnType, entity, tpe)
            } else {
              bindScalar(name, returnType, tpe)
            }
          entity.properties += (name -> p)
        }
    }
    entity
  }
  /**
   * support features
   * <li> buildin primary type will be not null
   */
  def merge(entity: Entity): Unit = {
    val cls = entity.clazz
    // search parent and interfaces
    var supclz: Class[_] = cls.getSuperclass
    val supers = new mutable.ListBuffer[Entity]
    cls.getInterfaces foreach { i =>
      if (mappings.contains(i)) supers += mappings(i)
    }
    while (supclz != null && supclz != classOf[Object]) {
      if (mappings.contains(supclz)) supers += mappings(supclz)
      supclz.getInterfaces foreach { i =>
        if (mappings.contains(i)) supers += mappings(i)
      }
      supclz = supclz.getSuperclass
    }

    val inheris = Collections.newMap[String, Property]
    supers.reverse foreach { e =>
      inheris ++= e.properties
      if (entity.idGenerator == None) e.idGenerator foreach (g => entity.idGenerator = Some(g))
      if (null == entity.cacheRegion && null == entity.cacheUsage) entity.cache(e.cacheRegion, e.cacheUsage)
    }

    val inherited = Collections.newMap[String, Property]
    entity.properties foreach {
      case (name, p) =>
        if (p.mergeable && inheris.contains(name)) inherited.put(name, inheris(name).clone())
    }
    entity.properties ++= inherited
  }

  private def bindComponent(name: String, propertyType: Class[_], entity: Entity, tpe: ru.Type): ComponentProperty = {
    val cp = new ComponentProperty(name, propertyType)
    val manifest = BeanManifest.get(propertyType, tpe)
    val ctpe = tpe.member(ru.TermName(name)).asMethod.returnType
    manifest.readables foreach {
      case (name, prop) =>
        if (prop.writable) {
          val resultType = prop.clazz
          val p =
            if (classOf[MEntity[_]].isAssignableFrom(resultType)) {
              bindManyToOne(name, resultType, ctpe)
            } else if (classOf[scala.collection.mutable.Seq[_]].isAssignableFrom(resultType)) {
              bindSeq(name, resultType, entity, ctpe)
            } else if (classOf[scala.collection.mutable.Set[_]].isAssignableFrom(resultType)) {
              bindSet(name, resultType, entity, ctpe)
            } else if (classOf[scala.collection.mutable.Map[_, _]].isAssignableFrom(resultType)) {
              bindMap(name, resultType, entity, ctpe)
            } else if (classOf[MComponent].isAssignableFrom(resultType)) {
              bindComponent(name, resultType, entity, ctpe)
            } else {
              bindScalar(name, resultType, ctpe)
            }
          cp.properties += (name -> p)
        }
    }
    cp
  }

  private def bindMap(name: String, propertyType: Class[_], entity: Entity, tye: ru.Type): MapProperty = {
    val p = new MapProperty(name, propertyType)
    val typeSignature = typeNameOf(tye, name)
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

  private def typeNameOf(tye: ru.Type, name: String): String = {
    tye.member(ru.TermName(name)).typeSignatureIn(tye).toString()
  }

  private def bindSeq(name: String, propertyType: Class[_], entity: Entity, tye: ru.Type): SeqProperty = {
    val p = new SeqProperty(name, propertyType)
    val typeSignature = typeNameOf(tye, name)
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    val m2m = new ToManyElement(entityName, new Column(columnName(entityName, true)))
    val key = new SimpleKey(new Column(columnName(entity.entityName, true)))

    p.element = Some(m2m)
    p.key = Some(key)
    p.index = Some(new Index(new Column("idx")))
    p
  }

  private def bindSet(name: String, propertyType: Class[_], entity: Entity, tye: ru.Type): SetProperty = {
    val p = new SetProperty(name, propertyType)
    val typeSignature = typeNameOf(tye, name)
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    val m2m = new ToManyElement(entityName, new Column(columnName(entityName, true)))
    val key = new SimpleKey(new Column(columnName(entity.entityName, true)))

    p.element = Some(m2m)
    p.key = Some(key)
    p
  }

  private def bindId(name: String, propertyType: Class[_], tye: ru.Type): IdProperty = {
    val p = new IdProperty(name, propertyType)
    val column = new Column(columnName(name))
    if (Primitives.isWrapperType(propertyType)) column.nullable = false
    p.columns += column
    p
  }

  private def bindScalar(name: String, propertyType: Class[_], tye: ru.Type): ScalarProperty = {
    val p = new ScalarProperty(name, propertyType)
    val column = new Column(columnName(name))
    if (propertyType == classOf[Option[_]]) {
      val a = tye.member(ru.TermName(name)).typeSignatureIn(tye)
      val innerType = a.resultType.typeArgs.head.toString
      val innerClass = ClassLoaders.loadClass(innerType)
      val primitiveClass = Primitives.unwrap(innerClass)
      p.typeName = Some(primitiveClass.getName + "?")
    } else if (Primitives.isWrapperType(propertyType)) {
      column.nullable = false
    }
    if (None == p.typeName) {
      p.typeName = Some(propertyType.getName)
    }
    p.columns += column
    p
  }

  private def bindManyToOne(name: String, propertyType: Class[_], tye: ru.Type): ManyToOneProperty = {
    val p = new ManyToOneProperty(name, propertyType)
    val column = new Column(columnName(name, true))
    p.targetEntity = propertyType.getName
    p.columns += column
    p
  }

}
