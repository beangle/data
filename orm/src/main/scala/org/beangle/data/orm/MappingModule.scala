/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.orm

import java.sql.{ Blob, Clob, Types }

import scala.collection.JavaConverters.asScalaSet
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.runtime.{ universe => ru }

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.{ Column, Identifier }
import org.beangle.data.model.meta.Domain.{ CollectionPropertyImpl, MapPropertyImpl, SingularPropertyImpl }
import org.beangle.data.model.meta.Property

object MappingModule {

  val OrderColumnName = "idx"

  trait Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit
  }

  class NotNull extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      ch.columns foreach (c => c.nullable = false)
    }
  }

  class Lob extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      val c = pm.property.asInstanceOf[Property].clazz
      var isBlob = false
      var isClob = false
      if (c.isArray) {
        if (c.getName.equals("[B") || c.getComponentType == classOf[java.lang.Byte]) {
          isBlob = true
        } else if (c.getName.equals("[C") || c.getComponentType == classOf[java.lang.Character]) {
          isClob = true
        }
      } else {
        //注意：这两个条件不要调整顺序，否则大部分类都是可序列化字类，回映射成blob
        if (c == classOf[Clob] || c == classOf[String]) {
          isClob = true
        } else if (c == classOf[Blob] || classOf[java.io.Serializable].isAssignableFrom(c)) {
          isBlob = true
        }
      }
      if (!isClob && !isBlob) {
        val p = pm.property.asInstanceOf[Property]
        throw new RuntimeException(s"Cannot mapping ${holder.clazz.getName}.${p.name}(${c.getName}) to lob!")
      } else {
        val engine = holder.mappings.database.engine
        if (isBlob) {
          ch.columns foreach (c => c.sqlType = engine.toType(Types.BLOB))
        } else {
          ch.columns foreach (c => c.sqlType = engine.toType(Types.CLOB))
        }
      }
    }
  }

  class Unique extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      ch.columns foreach (c => c.unique = true)
    }
  }

  class ElementColumn(name: String) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val mp = cast[PluralPropertyMapping[_]](pm, holder, "element column should used on PluralProperty")
      val ch = mp.element.asInstanceOf[ColumnHolder]
      ch.columns foreach (x => x.name = Identifier(name))
    }
  }

  class ElementLength(len: Int) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val mp = cast[PluralPropertyMapping[_]](pm, holder, "element length should used on PluralProperty")
      val ch = mp.element.asInstanceOf[ColumnHolder]
      ch.columns foreach (x => x.sqlType = holder.mappings.database.engine.toType(x.sqlType.code, len))
    }
  }

  class Cache(val cacheholder: CacheHolder) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val p = pm.property.asInstanceOf[Property]
      cacheholder.add(List(new Collection(holder.clazz, p.name)))
    }
  }

  private def refColumn(holder: EntityHolder[_], property: Option[String]): Column = {
    val mappings = holder.mappings
    val idType = BeanInfos.get(holder.mapping.clazz).getPropertyType("id").get
    val colName = property match {
      case Some(p) => holder.mappings.columnName(holder.mapping.clazz, p, true)
      case None    => holder.mappings.columnName(holder.mapping.clazz, holder.mapping.entityName, true)
    }
    new Column(mappings.database.engine.toIdentifier(colName), mappings.sqlTypeMapping.sqlType(idType), false)
  }

  class One2Many(targetEntity: Option[Class[_]], mappedBy: String, private var cascade: Option[String] = None) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val colpm = cast[PluralPropertyMapping[_]](pm, holder, "one2many should used on seq")
      colpm.ownerColumn = refColumn(holder, Some(mappedBy))
      targetEntity foreach { clazz =>
        colpm.property match {
          case cp: CollectionPropertyImpl => cp.element = holder.mappings.refEntity(clazz, clazz.getName)
          case mp: MapPropertyImpl        => mp.element = holder.mappings.refEntity(clazz, clazz.getName)
        }
        colpm.element = holder.mappings.refToOneMapping(clazz, clazz.getName)
      }
      colpm.one2many = true
      cascade foreach (c => colpm.cascade = Some(c))
    }

    def cascade(c: String, orphanRemoval: Boolean = true): this.type = {
      this.cascade = Some(if (orphanRemoval && !c.contains("delete-orphan")) c + ",delete-orphan" else c)
      this
    }

    def cascaded: this.type = {
      this.cascade = Some("all,delete-orphan")
      this
    }
  }

  class OrderBy(orderBy: String) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val cm = cast[CollectionPropertyMapping](pm, holder, "order by should used on seq");
      cm.property.asInstanceOf[CollectionPropertyImpl].orderBy = Some(orderBy)
    }
  }

  class Table(table: String) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      cast[PluralPropertyMapping[_]](pm, holder, "table should used on seq").table = Some(table)
    }
  }

  class ColumnName(name: String) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      if (ch.columns.size == 1) ch.columns.head.name = Identifier(name)
    }
  }

  class OrderColumn(orderColumn: String) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val collp = cast[CollectionPropertyMapping](pm, holder, "order column should used on many2many seq")
      val idxCol = new Column(Identifier(if (null == orderColumn) MappingModule.OrderColumnName else orderColumn), holder.mappings.sqlTypeMapping.sqlType(classOf[Int]), false)
      collp.index = Some(idxCol)
    }
  }

  class Length(len: Int) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      val engine = holder.mappings.database.engine
      ch.columns foreach (c => c.sqlType = engine.toType(c.sqlType.code, len, c.sqlType.precision.getOrElse(0), c.sqlType.scale.getOrElse(0)))
    }
  }

  class Target(clazz: Class[_]) extends Declaration {
    def apply(holder: EntityHolder[_], pm: PropertyMapping[_]): Unit = {
      val sp = pm.property.asInstanceOf[SingularPropertyImpl]
      sp.propertyType = holder.mappings.refEntity(clazz, clazz.getName)
    }
  }

  object Expression {
    // only apply unique on component properties
    def is(holder: EntityHolder[_], declarations: Seq[Declaration]): Unit = {
      val lasts = asScalaSet(holder.proxy.lastAccessed)
      if (!declarations.isEmpty && lasts.isEmpty) {
        throw new RuntimeException("Cannot find access properties for " + holder.mapping.entityName + " with declarations:" + declarations)
      }
      lasts foreach { property =>
        val pm = holder.mapping.getPropertyMapping(property)
        declarations foreach (d => d(holder, pm))
        pm.mergeable = false
      }
      lasts.clear()
    }
  }

  class Expression(val holder: EntityHolder[_]) {

    def is(declarations: Declaration*): Unit = {
      Expression.is(holder, declarations)
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
      Expression.is(holder, declarations)
    }
  }

  final class EntityHolder[T](val mapping: EntityTypeMapping, val mappings: Mappings, val clazz: Class[T], module: MappingModule) {

    var proxy: Proxy.EntityProxy = _

    def cacheable(): this.type = {
      mapping.cache(module.cacheConfig.region, module.cacheConfig.usage)
      this
    }

    def cache(region: String): this.type = {
      mapping.cacheRegion = region
      this
    }

    def usage(usage: String): this.type = {
      mapping.cacheUsage = usage
      this
    }

    def on(declarations: T => Any)(implicit manifest: Manifest[T]): this.type = {
      if (null == proxy) proxy = Proxy.generate(clazz)
      declarations(proxy.asInstanceOf[T])
      this
    }

    def generator(strategy: String): this.type = {
      mapping.idGenerator = new IdGenerator(strategy)
      this
    }

    def table(table: String): this.type = {
      val t = mapping.table
      t.name = Identifier(table)
      this
    }
  }

  final class CacheConfig(var region: String = null, var usage: String = null) {
  }

  final class CacheHolder(val mappings: Mappings, val cacheRegion: String, val cacheUsage: String) {
    def add(first: List[Collection], definitionLists: List[Collection]*): this.type = {
      first.foreach(d => mappings.addCollection(d.cache(cacheRegion, cacheUsage)))
      for (definitions <- definitionLists) {
        definitions.foreach(d => mappings.addCollection(d.cache(cacheRegion, cacheUsage)))
      }
      this
    }

    def add(first: Class[_ <: org.beangle.data.model.Entity[_]], classes: Class[_ <: org.beangle.data.model.Entity[_]]*): this.type = {
      mappings.getMapping(first).cache(cacheRegion, cacheUsage)
      for (clazz <- classes)
        mappings.getMapping(clazz).cache(cacheRegion, cacheUsage)
      this
    }
  }

  final class Entities(val entityMappings: collection.mutable.Map[String, EntityTypeMapping], cacheConfig: CacheConfig) {
    def except(clazzes: Class[_]*): this.type = {
      clazzes foreach { c => entityMappings -= c.getName }
      this
    }

    def cacheable(): Unit = {
      entityMappings foreach { e =>
        e._2.cacheRegion = cacheConfig.region
        e._2.cacheUsage = cacheConfig.usage
      }
    }

    def cache(region: String): this.type = {
      entityMappings foreach (e => e._2.cacheRegion = region)
      this
    }

    def usage(usage: String): this.type = {
      entityMappings foreach (e => e._2.cacheUsage = usage)
      this
    }
  }

  private def mismatch(msg: String, e: EntityTypeMapping, pm: PropertyMapping[_]): Unit = {
    val p = pm.property.asInstanceOf[Property]
    throw new RuntimeException(msg + s",Not for ${e.entityName}.${p.name}(${pm.getClass.getSimpleName}/${p.clazz.getName})")
  }

  private def mismatch(msg: String, e: EntityTypeMapping, p: Property): Unit = {
    throw new RuntimeException(msg + s",Not for ${e.entityName}.${p.name}(${p.getClass.getSimpleName}/${p.clazz.getName})")
  }

  private def cast[T](pm: PropertyMapping[_], holder: EntityHolder[_], msg: String)(implicit manifest: Manifest[T]): T = {
    if (!manifest.runtimeClass.isAssignableFrom(pm.getClass)) mismatch(msg, holder.mapping, pm)
    pm.asInstanceOf[T]
  }

  private def cast[T](p: Property, holder: EntityHolder[_], msg: String)(implicit manifest: Manifest[T]): T = {
    if (!manifest.runtimeClass.isAssignableFrom(p.getClass)) mismatch(msg, holder.mapping, p)
    p.asInstanceOf[T]
  }
}

@beta
abstract class MappingModule extends Logging {

  import MappingModule._
  private var currentHolder: EntityHolder[_] = _
  private var defaultIdGenerator: Option[String] = None
  private val cacheConfig = new CacheConfig()
  private val entityMappings = Collections.newMap[String, EntityTypeMapping]

  implicit def any2Expression(i: Any): Expression = {
    new Expression(currentHolder)
  }

  private var mappings: Mappings = _

  def binding(): Unit

  protected def declare[B](a: B*): Seq[B] = {
    a
  }

  protected def autoIncrement(): Unit = {
    defaultIdGenerator(IdGenerator.AutoIncrement)
  }

  protected def notnull = new NotNull

  protected def unique = new Unique

  protected def lob = new Lob

  protected def length(len: Int) = new Length(len)

  protected def cacheable: Cache = {
    new Cache(new CacheHolder(mappings, cacheConfig.region, cacheConfig.usage))
  }

  protected def cacheable(region: String, usage: String): Cache = {
    new Cache(new CacheHolder(mappings, region, usage))
  }

  protected def target[T](implicit manifest: Manifest[T]): Target = {
    new Target(manifest.runtimeClass)
  }

  protected def depends(clazz: Class[_], mappedBy: String): One2Many = {
    new One2Many(Some(clazz), mappedBy).cascaded
  }

  protected def depends(mappedBy: String): One2Many = {
    new One2Many(None, mappedBy).cascaded
  }

  protected def one2many(mappedBy: String): One2Many = {
    new One2Many(None, mappedBy)
  }

  protected def one2many(clazz: Class[_], mappedBy: String): One2Many = {
    new One2Many(Some(clazz), mappedBy)
  }

  protected def orderby(orderby: String): OrderBy = {
    new OrderBy(orderby)
  }

  protected def table(t: String): Table = {
    new Table(t)
  }

  protected def ordered: OrderColumn = {
    new OrderColumn(null)
  }

  protected def ordered(column: String): OrderColumn = {
    new OrderColumn(column)
  }

  protected def column(name: String): ColumnName = {
    new ColumnName(name)
  }

  protected def eleColumn(name: String): ElementColumn = {
    new ElementColumn(name)
  }

  protected def eleLength(len: Int): ElementLength = {
    new ElementLength(len)
  }

  protected final def bind[T: ClassTag]()(implicit manifest: Manifest[T], ttag: ru.TypeTag[T]): EntityHolder[T] = {
    bind(manifest.runtimeClass.asInstanceOf[Class[T]], null, ttag)
  }

  protected final def bind[T: ClassTag](entityName: String)(implicit manifest: Manifest[T], ttag: ru.TypeTag[T]): EntityHolder[T] = {
    bind(manifest.runtimeClass.asInstanceOf[Class[T]], entityName, ttag)
  }

  private def bind[T](cls: Class[T], entityName: String, ttag: ru.TypeTag[T]): EntityHolder[T] = {
    val mapping = mappings.autobind(cls, entityName, ttag.tpe)
    //find superclass's id generator
    var superCls: Class[_] = cls.getSuperclass
    while (null != superCls && superCls != classOf[Object]) {
      if (entityMappings.contains(superCls.getName)) {
        mapping.idGenerator = entityMappings(superCls.getName).idGenerator
        if (null != mapping.idGenerator) superCls = classOf[Object]
      }
      superCls = superCls.getSuperclass
    }

    if (null == mapping.idGenerator) {
      val unsaved = BeanInfos.get(cls, null).getPropertyType("id") match {
        case Some(idtype) => if (idtype.isPrimitive) "0" else "null"
        case None         => "null"
      }
      this.defaultIdGenerator foreach { a => mapping.idGenerator = new IdGenerator(a).unsaved(unsaved) }
    }
    val holder = new EntityHolder(mapping, mappings, cls, this)
    currentHolder = holder
    entityMappings.put(mapping.entityName, mapping)
    holder
  }

  protected final def defaultIdGenerator(strategy: String): Unit = {
    defaultIdGenerator = Some(strategy)
  }

  protected final def cache(region: String): CacheHolder = {
    new CacheHolder(mappings, region, cacheConfig.usage)
  }

  protected final def cache(): CacheHolder = {
    new CacheHolder(mappings, cacheConfig.region, cacheConfig.usage)
  }

  protected final def all: Entities = {
    val newEntities = Collections.newMap[String, EntityTypeMapping]
    new Entities(newEntities ++ entityMappings, cacheConfig)
  }

  protected final def collection[T](properties: String*)(implicit manifest: Manifest[T]): List[Collection] = {
    val definitions = new scala.collection.mutable.ListBuffer[Collection]
    val clazz = manifest.runtimeClass
    properties foreach (p => definitions += new Collection(clazz, p))
    definitions.toList
  }

  protected final def defaultCache(region: String, usage: String) {
    cacheConfig.region = region
    cacheConfig.usage = usage
  }

  final def configure(mappings: Mappings): Unit = {
    logger.info(s"Process ${getClass.getName}")
    this.mappings = mappings
    this.binding()
    entityMappings.clear()
  }

  def typedef(name: String, clazz: String, params: Map[String, String] = Map.empty): Unit = {
    mappings.addType(name, clazz, params)
  }

  def typedef(forClass: Class[_], clazz: String): Unit = {
    mappings.addType(forClass.getName, clazz, Map.empty)
  }

  def typedef(forClass: Class[_], clazz: String, params: Map[String, String]): Unit = {
    mappings.addType(forClass.getName, clazz, params)
  }
}
