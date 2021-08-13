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

package org.beangle.data.orm

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.reflect.{BeanInfo, BeanInfoDigger, BeanInfos}
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.meta.*

import java.sql.{Blob, Clob, Types}
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asScala
import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag

object MappingModule {

  val OrderColumnName = "idx"

  trait PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit
  }

  /** 创建索引
   *
   * 针对唯一索引，目前不支持空列
   *
   * @param name   indexname
   * @param unique unique index
   */
  class IndexDeclaration(name: String, unique: Boolean) {
    def apply(holder: EntityHolder[_], pms: Iterable[OrmProperty]): Unit = {
      // hibernate的index注解里没有支持unique，而是通过unique key支持的，为了保持一直，这里也类似处理
      // 这样和hibernate的sql输出思路类似
      if (unique) {
        val uk = new UniqueKey(holder.mapping.table, Identifier(name))
        pms.foreach { pm =>
          val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
          ch.columns.find(_.nullable) foreach { nullCol =>
            throw new RuntimeException(s"Cannot create unique index $name on ${holder.mapping.table.name},nullable column ${nullCol.name} finded!")
          }
          ch.columns.foreach(e => uk.addColumn(e.name))
        }
        if (Strings.isBlank(name)) {
          uk.name = Identifier(Constraint.autoname(uk))
        }
        holder.mapping.table.uniqueKeys += uk
      } else {
        val idx = new Index(holder.mapping.table, Identifier(name))
        idx.unique = false
        pms.foreach { pm =>
          val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
          ch.columns.foreach(e => idx.addColumn(e.name))
        }
        if (Strings.isBlank(name)) {
          idx.name = Identifier(Constraint.autoname(idx))
        }
        holder.mapping.table.indexes += idx
      }
    }
  }

  class NotNull extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      ch.columns foreach (c => c.nullable = false)
    }
  }

  /** 不可更新，不可插入 */
  class ReadOnly extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      pm.updateable = false
      pm.insertable = false
    }
  }

  /** 不可更新，但可插入 */
  class Immutable extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      pm.updateable = false
      pm.insertable = true
    }
  }

  class Lob extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      val c = pm.clazz
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
        throw new RuntimeException(s"Cannot mapping ${holder.clazz.getName}.${pm.name}(${c.getName}) to lob!")
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

  class Unique extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      ch.columns foreach (c => c.unique = true)
    }
  }

  class KeyColumn(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val mp = cast[OrmMapProperty](pm, holder, "key column should used on MapProperty")
      mp.keyColumn.name = Identifier(name)
    }
  }

  class KeyLength(len: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val mp = cast[OrmMapProperty](pm, holder, "key length should used on MapProperty")
      val x = mp.keyColumn
      mp.keyColumn.sqlType = holder.mappings.database.engine.toType(x.sqlType.code, len)
    }
  }

  class ElementColumn(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val mp = cast[OrmPluralProperty](pm, holder, "element column should used on PluralProperty")

      mp.element match {
        case ch: OrmBasicType => ch.columns foreach (x => x.name = Identifier(name))
        case _: OrmEntityType => mp.inverseColumn foreach (x => x.name = Identifier(name))
        case _ =>
      }
    }
  }

  class ElementLength(len: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val mp = cast[OrmPluralProperty](pm, holder, "element length should used on PluralProperty")
      mp.element match {
        case ch: OrmBasicType => ch.columns foreach (x => x.sqlType = holder.mappings.database.engine.toType(x.sqlType.code, len))
        case _: OrmEntityType => mp.inverseColumn foreach (x => x.sqlType = holder.mappings.database.engine.toType(x.sqlType.code, len))
        case _ =>
      }
    }
  }

  class JoinColumn(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val mp = cast[OrmPluralProperty](pm, holder, "element column should used on PluralProperty")
      if (null != mp.ownerColumn) {
        mp.ownerColumn.name = Identifier(name)
      }
    }
  }

  class Cache(val cacheholder: CacheHolder) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      cacheholder.add(List(new Collection(holder.clazz, pm.name)))
    }
  }

  private def genOwnerColumn(holder: EntityHolder[_], mappedBy: Option[String]): Column = {
    val mappings = holder.mappings
    val idType = BeanInfos.load(holder.mapping.clazz).getPropertyType("id").get
    val colName = mappedBy match {
      case Some(p) => holder.mappings.columnName(holder.mapping.clazz, p, key = true)
      case None => holder.mappings.columnName(holder.mapping.clazz, holder.mapping.entityName, key = true)
    }
    new Column(mappings.database.engine.toIdentifier(colName), mappings.sqlTypeMapping.sqlType(idType), false)
  }

  class Many2Many(mappedBy: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val colpm = cast[OrmCollectionProperty](pm, holder, "many2many should used on seq")
      colpm.mappedBy = Some(mappedBy)
      if (!colpm.element.isInstanceOf[OrmEntityType]) {
        MappingMacro.mismatch("many2many with mappedBy should be applied on entity", holder.mapping, pm)
      }
      colpm.table = None
    }
  }

  class One2Many(targetEntity: Option[Class[_]], mappedBy: String, private var cascade: Option[String] = None) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val colpm = cast[OrmCollectionProperty](pm, holder, "one2many should used on seq")
      colpm.ownerColumn = genOwnerColumn(holder, Some(mappedBy))
      colpm.mappedBy = Some(mappedBy)
      targetEntity foreach { clazz =>
        colpm.element = holder.mappings.refEntity(clazz, clazz.getName)
        colpm.inverseColumn = Some(holder.mappings.newRefColumn(clazz, clazz.getName))
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

  class OrderBy(orderBy: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val cm = cast[OrmCollectionProperty](pm, holder, "order by should used on seq")
      cm.orderBy = Some(orderBy)
    }
  }

  class Table(table: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      cast[OrmPluralProperty](pm, holder, "table should used on seq").table = Some(table)
    }
  }

  class ColumnName(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      if (ch.columns.size == 1) ch.columns.head.name = Identifier(name)
    }
  }

  class OrderColumn(orderColumn: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val collp = cast[OrmCollectionProperty](pm, holder, "order column should used on many2many seq")
      val idxCol = new Column(Identifier(if (null == orderColumn) MappingModule.OrderColumnName else orderColumn), holder.mappings.sqlTypeMapping.sqlType(classOf[Int]), false)
      idxCol.comment = Some("index no")
      collp.index = Some(idxCol)
    }
  }

  class Length(len: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val ch = cast[ColumnHolder](pm, holder, "Column holder needed")
      val engine = holder.mappings.database.engine
      ch.columns foreach (c => c.sqlType = engine.toType(c.sqlType.code, len, c.sqlType.scale.getOrElse(0)))
    }
  }

  class Target(clazz: Class[_]) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], pm: OrmProperty): Unit = {
      val sp = pm.asInstanceOf[OrmSingularProperty]
      sp.propertyType = holder.mappings.refEntity(clazz, clazz.getName)
    }
  }

  object Expression {
    // only apply unique on component properties
    def is(holder: EntityHolder[_], declarations: Seq[PropertyDeclaration]): Unit = {
      val lasts = asScala(holder.proxy.lastAccessed)
      if (declarations.nonEmpty && lasts.isEmpty) {
        throw new RuntimeException("Cannot find access properties for " + holder.mapping.entityName + " with declarations:" + declarations)
      }
      lasts foreach { name =>
        val pm = holder.mapping.property(name)
        declarations foreach (d => d(holder, pm))
        pm.mergeable = false
      }
      lasts.clear()
    }
  }

  class Expression(val holder: EntityHolder[_]) {

    def is(declarations: PropertyDeclaration*): Unit = {
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

    def are(declarations: PropertyDeclaration*): Unit = {
      Expression.is(holder, declarations)
    }
  }

  final class EntityHolder[T](val mapping: OrmEntityType, val mappings: Mappings, val clazz: Class[T], module: MappingModule) {

    var proxy: Proxy.EntityProxy = _

    def cacheable(): this.type = {
      mappings.cache(mapping, module.cacheConfig.region, module.cacheConfig.usage)
      this
    }

    def cacheAll(region: String = module.cacheConfig.region, usage: String = module.cacheConfig.usage): this.type = {
      mappings.cacheAll(mapping, region, usage)
      this
    }

    def cache(region: String, usage: String): this.type = {
      mappings.cache(mapping, region, usage)
      this
    }

    def declare(declarations: T => Any): this.type = {
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
      mappings.getEntity(first).cache(cacheRegion, cacheUsage)
      for (clazz <- classes)
        mappings.getEntity(clazz).cache(cacheRegion, cacheUsage)
      this
    }
  }

  final class Entities(val mappings: Mappings,
                       val entityMappings: mutable.Map[String, OrmEntityType],
                       cacheConfig: CacheConfig) {
    def except(clazzes: Class[_]*): this.type = {
      clazzes foreach { c => entityMappings -= c.getName }
      this
    }

    def cacheable(): Unit = {
      cache(cacheConfig.region, cacheConfig.usage)
    }

    def cache(region: String, usage: String): this.type = {
      entityMappings foreach { e =>
        mappings.cache(e._2, cacheConfig.region, cacheConfig.usage)
      }
      this
    }

    def cacheAll(region: String = cacheConfig.region, usage: String = cacheConfig.usage): this.type = {
      entityMappings foreach { e =>
        mappings.cacheAll(e._2, cacheConfig.region, cacheConfig.usage)
      }
      this
    }
  }

  inline def cast[T](pm: OrmProperty, holder: EntityHolder[_], msg: String) : T =
    ${MappingMacro.castImpl[T]('pm,'holder,'msg)}
}

@beta
abstract class MappingModule(var name: Option[String]) extends Logging {

  import MappingModule._

  private var currentHolder: EntityHolder[_] = _
  private val defaultIdGenerators = Collections.newMap[Class[_], String]
  private val cacheConfig = new CacheConfig()
  private val entityMappings = Collections.newMap[String, OrmEntityType]
  private var mappings: Mappings = _

  init()

  import scala.language.implicitConversions

  implicit def any2Expression(i: Any): Expression = {
    new Expression(currentHolder)
  }

  def this() = {
    this(None)
  }

  def binding(): Unit

  protected def init(): Unit = {
    defaultIdGenerator(classOf[Int], IdGenerator.AutoIncrement)
    defaultIdGenerator(classOf[Long], IdGenerator.DateTime)
    defaultIdGenerator(classOf[String], IdGenerator.Uuid)
  }

  protected def autoIncrement(): Unit = {
    defaultIdGenerator(classOf[Int], IdGenerator.AutoIncrement)
    defaultIdGenerator(classOf[Long], IdGenerator.AutoIncrement)
  }

  protected def notnull = new NotNull

  protected def unique = new Unique

  protected def readOnly = new ReadOnly

  protected def immutable = new Immutable

  protected def lob = new Lob

  protected def length(len: Int) = new Length(len)

  protected def cacheable: Cache = new Cache(new CacheHolder(mappings, cacheConfig.region, cacheConfig.usage))

  protected def cacheable(region: String, usage: String): Cache = new Cache(new CacheHolder(mappings, region, usage))

  protected inline def target[T]: Target = ${MappingMacro.target[T]}

  protected def depends(clazz: Class[_], mappedBy: String): One2Many = new One2Many(Some(clazz), mappedBy).cascaded

  protected def depends(mappedBy: String): One2Many = new One2Many(None, mappedBy).cascaded

  protected def one2many(mappedBy: String): One2Many = new One2Many(None, mappedBy)

  protected def one2many(clazz: Class[_], mappedBy: String): One2Many = new One2Many(Some(clazz), mappedBy)

  protected def many2many(mappedBy: String): Many2Many = new Many2Many(mappedBy)

  protected def orderby(orderby: String): OrderBy = new OrderBy(orderby)

  protected def table(t: String): Table = new Table(t)

  protected def ordered: OrderColumn = new OrderColumn(null)

  protected def ordered(column: String): OrderColumn = new OrderColumn(column)

  protected def column(name: String): ColumnName = new ColumnName(name)

  protected def keyColumn(name: String): KeyColumn = new KeyColumn(name)

  protected def keyLength(len: Int): KeyLength = new KeyLength(len)

  protected def eleColumn(name: String): ElementColumn = new ElementColumn(name)

  protected def eleLength(len: Int): ElementLength = new ElementLength(len)

  protected def joinColumn(name: String): JoinColumn = new JoinColumn(name)

  protected inline def bind[T: ClassTag]: EntityHolder[T] = ${MappingMacro.bind[T]('{""},'this)}

  protected inline def bind[T: ClassTag](entityName: String): EntityHolder[T] = ${MappingMacro.bind[T]('entityName,'this)}

  def bindImpl[T](cls: Class[T], entityName: String,bi:BeanInfo): EntityHolder[T] = {
    val mapping = mappings.autobind(cls, entityName, bi)
    //find superclass's id generator
    var superCls: Class[_] = cls.getSuperclass
    while (null != superCls && superCls != classOf[Object]) {
      if (entityMappings.contains(superCls.getName)) {
        mapping.idGenerator = entityMappings(superCls.getName).idGenerator
        if (null != mapping.idGenerator) superCls = classOf[Object]
      }
      superCls = superCls.getSuperclass
    }

    //find id genertor by id type
    if (null == mapping.idGenerator) {
      bi.getPropertyType("id") foreach { idtype =>
        val unsaved = if (idtype.isPrimitive) "0" else "null"
        mapping.idGenerator = defaultIdGenerators.get(idtype) match {
          case Some(ig) => new IdGenerator(ig).unsaved(unsaved)
          case None => new IdGenerator(IdGenerator.Assigned).unsaved(unsaved)
        }
      }
    }
    val holder = new EntityHolder(mapping, mappings, cls, this)
    mapping.module = this.name
    currentHolder = holder
    entityMappings.put(mapping.entityName, mapping)
    holder
  }

  protected final def defaultIdGenerator(clazz: Class[_], strategy: String): Unit = {
    defaultIdGenerators.put(clazz, strategy)
  }

  protected final def cache(region: String, usage: String): CacheHolder = {
    new CacheHolder(mappings, region, usage)
  }

  protected final def cache(): CacheHolder = {
    new CacheHolder(mappings, cacheConfig.region, cacheConfig.usage)
  }

  protected final def all: Entities = {
    val newEntities = Collections.newMap[String, OrmEntityType]
    new Entities(mappings, newEntities ++ entityMappings, cacheConfig)
  }

  protected final inline def collection[T](inline properties: String*): List[Collection] = ${MappingMacro.collection[T]('properties)}

  protected final def defaultCache(region: String, usage: String): Unit = {
    cacheConfig.region = region
    cacheConfig.usage = usage
  }

  final def configure(mappings: Mappings): Unit = {
    if (logger.isDebugEnabled) {
      logger.debug(s"Process ${getClass.getName}")
    }
    this.mappings = mappings
    this.binding()
    entityMappings.clear()
  }

  def index(name: String, unique: Boolean, properties: Any*): Unit = {
    val lasts = currentHolder.proxy.lastAccessed
    if (lasts.isEmpty) {
      throw new RuntimeException("Cannot find access properties for " + currentHolder.mapping.entityName + " with index declarations")
    }
    val mapping = currentHolder.mapping
    val pms = Collections.newBuffer[OrmProperty]
    //Don't wrap java.util.LinkedHashSet to scala set,It will lost order.
    val i = lasts.iterator()
    while (i.hasNext) {
      pms += mapping.property(i.next())
    }
    new IndexDeclaration(name, unique).apply(currentHolder, pms)
    lasts.clear()
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
