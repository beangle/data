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
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.model.Component
import org.beangle.data.model.bind.Jpas.{ isMap, isSeq, isSet, isEntity, isComponent }
import javassist.{ ClassPool, CtConstructor, CtField, CtMethod, LoaderClassPath }
import javassist.compiler.Javac
import org.beangle.commons.lang.annotation.value

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

    def getSimpleProperty(property: String): Property = {
      properties.get(property) match {
        case Some(p) => p
        case None    => throw new RuntimeException("Cannot find property " + property + " in " + entityName)
      }
    }

    def getProperty(property: String): Property = {
      val idx = property.indexOf(".")
      if (idx == -1) getSimpleProperty(property)
      else getSimpleProperty(property.substring(0, idx)).asInstanceOf[ComponentProperty].getProperty(property.substring(idx + 1))
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

  final class Column(var name: String, var nullable: Boolean = true) extends Cloneable {
    var length: Option[Int] = None
    var scale: Option[Int] = None
    var precision: Option[Int] = None
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
      old foreach (c => cloned.columns += c.clone())
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

  final class SeqProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType)

  final class SetProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType)

  final class MapProperty(name: String, propertyType: Class[_]) extends CollectionProperty(name, propertyType) {
    var mapKey: Key = _
  }

  class Key

  class SimpleKey(column: Column, tpeName: String = null) extends Key with ColumnHolder with TypeNameHolder {
    if (null != column) columns += column
    if (null != tpeName) this.typeName = Some(tpeName)
  }

  class CompositeKey extends Key with Component

  class ManyToOneKey(val entityName: String) extends Key with ColumnHolder {
    columns += ref(entityName)
  }

  class Index(column: Column) extends ColumnHolder with TypeNameHolder {
    columns += column
  }

  trait Component {
    var clazz: Option[String] = None
    val properties = Collections.newMap[String, Property]
    var parentProperty: Option[String] = None
    def getProperty(property: String): Property = {
      val idx = property.indexOf(".")
      if (idx == -1) properties(property)
      else properties(property.substring(0, idx)).asInstanceOf[ComponentProperty].getProperty(property.substring(idx + 1))
    }
  }

  class Element

  class ToManyElement(var entityName: String, column: Column) extends Element with ColumnHolder {
    var one2many = false
    if (null != column) columns += column

    def this(entityName: String) {
      this(entityName, ref(entityName))
    }

    def many2many: Boolean = {
      !one2many
    }
  }

  class CompositeElement extends Element with Component

  class SimpleElement(column: Column, tpeName: String = null) extends Element with ColumnHolder with TypeNameHolder {
    if (null != column) columns += column
    if (null != tpeName) this.typeName = Some(tpeName)
  }

  trait ModelProxy {
    def lastAccessed(): java.util.Set[String]
  }

  trait EntityProxy extends ModelProxy

  trait ComponentProxy extends ModelProxy {
    def setParent(proxy: ModelProxy): Unit
  }

  def columnName(propertyName: String, key: Boolean = false): String = {
    val lastDot = propertyName.lastIndexOf(".")
    val columnName = if (lastDot == -1) s"@${propertyName}" else "@" + propertyName.substring(lastDot + 1)
    if (key) columnName + "Id" else columnName
  }

  class TypeDef(val clazz: String, val params: Map[String, String])

  def ref(entityName: String): Column = {
    new Column(columnName(entityName, true), false)
  }
}
/**
 * @author chaostone
 * @since 3.1
 */
final class Binder {

  import Binder._
  private var pool = ClassPool.getDefault
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
   * Buildin value types
   */
  val valueTypes = new mutable.HashSet[Class[_]]

  /**
   * Buildin enum types
   */
  val enumTypes = new mutable.HashMap[String, String]

  /**
   * Classname.property -> Collection
   */
  val collectMap = new mutable.HashMap[String, Collection]

  /**
   * Only entities
   */
  val entities = Collections.newMap[String, Entity]

  def collections: Iterable[Collection] = collectMap.values

  def getEntity(clazz: Class[_]): Entity = mappings(clazz)

  def addEntity(entity: Entity): this.type = {
    val cls = entity.clazz
    mappings.put(cls, entity)
    if (!cls.isInterface() && !Modifier.isAbstract(cls.getModifiers)) {
      //replace super entity with same entityName
      //It's very strange,hibnerate ClassMetadata with has same entityName and mappedClass in type overriding,
      //So, we leave  hibernate a  clean world.
      entities.get(entity.entityName) match {
        case Some(o) => if (o.clazz.isAssignableFrom(entity.clazz)) entities.put(entity.entityName, entity)
        case None    => entities.put(entity.entityName, entity)
      }
    }
    this
  }

  def addCollection(definition: Collection): this.type = {
    collectMap.put(definition.clazz.getName() + definition.property, definition)
    this
  }

  def addType(name: String, clazz: String, params: Map[String, String]): Unit = {
    types.put(name, new TypeDef(clazz, params))
  }

  def generateProxy(clazz: Class[_]): EntityProxy = {
    val proxyClassName = clazz.getSimpleName + "_proxy"
    val fullClassName = clazz.getName + "_proxy"
    try {
      val proxyCls = ClassLoaders.load(fullClassName)
      return proxyCls.getConstructor().newInstance().asInstanceOf[EntityProxy]
    } catch {
      case e: Exception =>
    }
    val cct = pool.makeClass(fullClassName)
    if (clazz.isInterface) cct.addInterface(pool.get(clazz.getName))
    else cct.setSuperclass(pool.get(clazz.getName))
    cct.addInterface(pool.get(classOf[EntityProxy].getName))
    val javac = new Javac(cct)
    cct.addField(javac.compile("public java.util.Set _lastAccessed;").asInstanceOf[CtField])

    val manifest = BeanManifest.get(clazz)
    val componentValues = Collections.newMap[Method, ComponentProxy]
    manifest.properties foreach {
      case (name, p) =>
        if (p.readable) {
          val getter = p.getter.get
          val value = Primitives.defaultLiteral(p.clazz)
          val body = s"public ${p.clazz.getName} ${getter.getName}() { return $value;}"
          val ctmod = javac.compile(body).asInstanceOf[CtMethod]
          if (isComponent(p.clazz)) {
            val cproxy = generateComponentProxy(p.clazz, name + ".")
            componentValues += (p.setter.get -> cproxy)
            ctmod.setBody("{_lastAccessed.add(\"" + name + "\");return super." + getter.getName + "();}")
          } else {
            ctmod.setBody("{_lastAccessed.add( \"" + name + "\");return " + value + ";}")
          }
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
    val proxy = maked.getConstructor().newInstance().asInstanceOf[EntityProxy]
    componentValues foreach {
      case (method, component) =>
        method.invoke(proxy, component)
        component.setParent(proxy)
    }
    proxy
  }

  private def generateComponentProxy(clazz: Class[_], path: String): ComponentProxy = {
    val proxyClassName = clazz.getSimpleName + "_proxy"
    val fullClassName = clazz.getName + "_proxy"
    try {
      val proxyCls = ClassLoaders.load(fullClassName)
      return proxyCls.getConstructor(classOf[String]).newInstance(path).asInstanceOf[ComponentProxy]
    } catch {
      case e: Exception =>
    }
    val cct = pool.makeClass(fullClassName)
    if (clazz.isInterface()) cct.addInterface(pool.get(clazz.getName))
    else cct.setSuperclass(pool.get(clazz.getName))
    cct.addInterface(pool.get(classOf[ComponentProxy].getName))
    val javac = new Javac(cct)

    cct.addField(javac.compile("public " + classOf[ModelProxy].getName + " _parent;").asInstanceOf[CtField])
    cct.addField(javac.compile("public java.lang.String _path=null;").asInstanceOf[CtField])

    val manifest = BeanManifest.get(clazz)
    val componentValues = Collections.newMap[Method, ComponentProxy]
    manifest.properties foreach {
      case (name, p) =>
        if (p.readable) {
          val getter = p.getter.get
          val value = Primitives.defaultLiteral(p.clazz)
          val body = s"public ${p.clazz.getName} ${getter.getName}() { return $value;}"
          val ctmod = javac.compile(body).asInstanceOf[CtMethod]

          val accessed = "_parent.lastAccessed()"
          if (isComponent(p.clazz)) {
            val cproxy = generateComponentProxy(p.clazz, path + name + ".")
            componentValues += (p.setter.get -> cproxy)
            ctmod.setBody("{" + accessed + ".add(_path + \"" + name + "\");return super." + getter.getName + "();}")
          } else {
            val value = Primitives.defaultLiteral(p.clazz)
            ctmod.setBody("{" + accessed + ".add(_path + \"" + name + "\");return " + value + ";}")
          }
          cct.addMethod(ctmod)
        }
    }
    val ctor = javac.compile("public " + proxyClassName + "(String path){}").asInstanceOf[CtConstructor]
    ctor.setBody("{this._parent=null;this._path=$1;}")
    cct.addConstructor(ctor)

    //implement setParent and lastAccessed
    var ctmod = javac.compile("public void setParent(" + classOf[ModelProxy].getName + " proxy) { return null;}").asInstanceOf[CtMethod]
    ctmod.setBody("{this._parent=$1;}")
    cct.addMethod(ctmod)
    ctmod = javac.compile("public java.util.Set lastAccessed() { return null;}").asInstanceOf[CtMethod]
    ctmod.setBody("{return _parent.lastAccessed();}")
    cct.addMethod(ctmod)

    //    cct.debugWriteFile("/tmp/handlers")
    val maked = cct.toClass()
    cct.detach()
    val proxy = maked.getConstructor(classOf[String]).newInstance(path).asInstanceOf[ComponentProxy]
    //support nested component
    componentValues foreach {
      case (method, component) =>
        method.invoke(proxy, component)
        component.setParent(proxy)
    }
    proxy
  }

  def autobind(): Unit = {
    //superclass first
    mappings.keys.toList.sortWith { (a, b) => a.isAssignableFrom(b) } foreach (cls => merge(mappings(cls)))
  }

  def clear(): Unit = {
    this.entities.clear()
    this.mappings.clear()
    this.collectMap.clear()
    this.types.clear()
    this.valueTypes.clear()
    this.enumTypes.clear()
    this.pool = null
  }

  def autobind(cls: Class[_], entityName: String, tpe: ru.Type): Entity = {
    val entity = if (entityName == null) new Entity(cls) else new Entity(cls, entityName)
    if (cls.isAnnotationPresent(classOf[javax.persistence.Entity])) return entity;
    val manifest = BeanManifest.get(entity.clazz, tpe)
    manifest.readables foreach {
      case (name, prop) =>
        if (prop.readable & prop.writable) {
          val propType = prop.clazz
          val p =
            if (name == "id") {
              bindId(name, propType, tpe)
            } else if (isEntity(propType)) {
              bindManyToOne(name, propType, tpe)
            } else if (isSeq(propType)) {
              bindSeq(name, propType, entity, tpe)
            } else if (isSet(propType)) {
              bindSet(name, propType, entity, tpe)
            } else if (isMap(propType)) {
              bindMap(name, propType, entity, tpe)
            } else if (isComponent(propType)) {
              bindComponent(name, propType, entity, tpe)
            } else {
              bindScalar(name, propType, scalarTypeName(name, propType, tpe))
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
    cls.getInterfaces foreach (i => if (mappings.contains(i)) supers += mappings(i))
    while (supclz != null && supclz != classOf[Object]) {
      if (mappings.contains(supclz)) supers += mappings(supclz)
      supclz.getInterfaces foreach (i => if (mappings.contains(i)) supers += mappings(i))
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
          val propType = prop.clazz
          val p =
            if (isEntity(propType)) {
              if (propType == entity.clazz) {
                cp.parentProperty = Some(name); null.asInstanceOf[Property]
              } else {
                bindManyToOne(name, propType, ctpe)
              }
            } else if (isSeq(propType)) {
              bindSeq(name, propType, entity, ctpe)
            } else if (isSet(propType)) {
              bindSet(name, propType, entity, ctpe)
            } else if (isMap(propType)) {
              bindMap(name, propType, entity, ctpe)
            } else if (isComponent(propType)) {
              bindComponent(name, propType, entity, ctpe)
            } else {
              bindScalar(name, propType, scalarTypeName(name, propType, ctpe))
            }
          if (null != p) cp.properties += (name -> p)
        }
    }
    cp
  }

  private def scalarTypeName(name: String, clazz: Class[_], tpe: ru.Type): String = {
    if (clazz == classOf[Option[_]]) {
      val a = tpe.member(ru.TermName(name)).typeSignatureIn(tpe)
      val innerType = a.resultType.typeArgs.head.toString
      val innerClass = ClassLoaders.load(innerType)
      val primitiveClass = Primitives.unwrap(innerClass)
      primitiveClass.getName + "?"
    } else if (clazz.isAnnotationPresent(classOf[value])) {
      valueTypes += clazz
      clazz.getName
    } else if (classOf[Enumeration#Value].isAssignableFrom(clazz)) {
      val typeName = tpe.member(ru.TermName(name)).asMethod.returnType.toString
      enumTypes.put(typeName, Strings.substringBeforeLast(typeName, "."))
      typeName
    } else clazz.getName
  }

  private def bindMap(name: String, propertyType: Class[_], entity: Entity, tye: ru.Type): MapProperty = {
    val p = new MapProperty(name, propertyType)
    val typeSignature = typeNameOf(tye, name)
    val kvtype = Strings.substringBetween(typeSignature, "[", "]")

    var mapKeyType = Strings.substringBefore(kvtype, ",").trim
    var mapEleType = Strings.substringAfter(kvtype, ",").trim
    mapKeyType = if (mapKeyType.contains(".")) mapKeyType else "java.lang." + mapKeyType
    mapEleType = if (mapEleType.contains(".")) mapEleType else "java.lang." + mapEleType

    p.mapKey =
      if (isEntity(ClassLoaders.load(mapKeyType))) new ManyToOneKey(mapKeyType)
      else new SimpleKey(new Column("name", false), mapKeyType)

    val mapElem =
      if (isEntity(ClassLoaders.load(mapEleType))) new ToManyElement(mapEleType)
      else new SimpleElement(new Column("value", false), mapEleType)

    if (propertyType.getName.startsWith("scala.")) p.typeName = Some("map")
    p.key = Some(new SimpleKey(ref(entity.entityName)))
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
    p.element = Some(new ToManyElement(entityName))
    p.key = Some(new SimpleKey(ref(entity.entityName)))
    if (propertyType.getName.startsWith("scala.")) p.typeName = Some("seq")
    p
  }

  private def bindSet(name: String, propertyType: Class[_], entity: Entity, tye: ru.Type): SetProperty = {
    val p = new SetProperty(name, propertyType)
    val typeSignature = typeNameOf(tye, name)
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    if (propertyType.getName.startsWith("scala.")) p.typeName = Some("set")
    p.element = Some(new ToManyElement(entityName))
    p.key = Some(new SimpleKey(ref(entity.entityName)))
    p
  }

  private def bindId(name: String, propertyType: Class[_], tye: ru.Type): IdProperty = {
    val p = new IdProperty(name, propertyType)
    val column = new Column(columnName(name), false)
    if (Primitives.isWrapperType(propertyType)) column.nullable = false
    p.columns += column
    p
  }

  private def bindScalar(name: String, propertyType: Class[_], typeName: String): ScalarProperty = {
    val p = new ScalarProperty(name, propertyType)
    val column = new Column(columnName(name))
    if (propertyType.isPrimitive) column.nullable = false
    if (None == p.typeName) p.typeName = Some(typeName)
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