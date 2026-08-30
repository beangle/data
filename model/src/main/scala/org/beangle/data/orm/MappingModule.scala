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

import org.beangle.commons.bean.meta.{MetaDigger, MetaRegistrar}
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.beta
import org.beangle.commons.lang.reflect.{BeanInfo, BeanInfos}
import org.beangle.commons.xml.Document
import org.beangle.data.Logger
import org.beangle.data.dao.Prop
import org.beangle.data.orm.cfg.Profiles
import org.beangle.jdbc.engine.{Engine, Engines}
import org.beangle.jdbc.meta.*

import java.sql.{Blob, Clob, Types}
import scala.collection.mutable
import scala.quoted.{Expr, Quotes, Type}
import scala.reflect.ClassTag

object MappingModule {

  val OrderColumnName = "idx"

  /** 构建期独立实例化（MetaGenerator/AotHintGenerator 经 registering() 触发）时自举的 Mappings，
   * 仅用于让绑定 DSL 在无真实配置下可执行；运行期 configure() 会替换为真实 Mappings。
   */
  private lazy val buildMappings: Mappings = {
    val mappings = new Mappings(new Database(Engines.forName("H2")), new Profiles(new Document("beangle")))
    mappings.autobind() // 空跑初始化 messages，与运行期 LocalSessionFactoryBean/Mappings.autobind 前置一致
    mappings
  }

  def mismatch(msg: String, e: OrmEntityType, pm: OrmProperty): Unit = {
    throw new RuntimeException(msg + s",Not for ${e.entityName}.${pm.name}(${pm.getClass.getSimpleName}/${pm.clazz.getName})")
  }

  /** Macro: 构建期（buildTime）用编译期挖掘的 BeanMeta（addMetas 收集 + bindImpl 干跑，精确类型不依赖
   * beanmeta.idx）；运行期走 BeanInfos.get —— 精确类型来自构建期生成的 beanmeta.idx（MetaModels
   * 加载），反射只是无 idx 时的回退。
   */
  def bind[T: Type](entityName: Expr[String], module: Expr[MappingModule])(implicit quotes: Quotes): Expr[EntityHolder[T]] = {
    import quotes.reflect.*
    val clazzSym = Symbol.requiredMethod("scala.Predef.classOf")
    val clzz = TypeApply(Select(Ref(defn.PredefModule), clazzSym), List(TypeTree.of[T])).asExpr.asInstanceOf[Expr[Class[T]]]
    val cm = MetaDigger.digInto[T](clzz)
    '{
      val bi =
        if ${ module }.buildTime then
          val bm = ${ cm } // 编译期挖掘一次，构建期构造一次
          val bmInfo = BeanInfo.from(bm)
          ${ module }.addMetas(Seq(bm))
          BeanInfos.update(bmInfo) // 供绑定 DSL 内部 BeanInfos.get（如 genOwnerColumn）精确查询
          bmInfo
        else BeanInfos.get(${ clzz })
      if Strings.isBlank(${ entityName }) then
        ${ module }.bindImpl(${ clzz }, ${ clzz }.getName, bi)
      else
        ${ module }.bindImpl(${ clzz }, ${ entityName }, bi)
    }
  }

  trait PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit
  }

  /** 创建索引
   *
   * 针对唯一索引，目前不支持允许为空的列
   *
   * @param name   index name
   * @param unique unique index
   */
  class IndexDeclaration(name: String, unique: Boolean) {
    def apply(holder: EntityHolder[_], pms: Iterable[OrmProperty]): Unit = {
      // hibernate的index注解里没有支持unique，而是通过unique key支持的，为了保持一直，这里也类似处理
      // 这样和hibernate的sql输出思路类似
      if (unique) {
        val uk = new UniqueKey(holder.mapping.table, Identifier(name))
        pms.foreach { pm =>
          val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
          ch.columns.find(_.nullable) foreach { nullCol =>
            throw new RuntimeException(s"Cannot create unique index $name on ${holder.mapping.table.name},nullable column ${nullCol.name} finded!")
          }
          ch.columns.foreach(e => uk.addColumn(e.name))
        }
        if Strings.isBlank(name) then uk.name = Identifier(Constraint.autoname(uk))
        holder.mapping.table.add(uk)
      } else {
        val idx = new Index(holder.mapping.table, Identifier(name))
        idx.unique = false
        pms.foreach { pm =>
          val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
          ch.columns.foreach(e => idx.addColumn(e.name))
        }
        if (Strings.isBlank(name)) {
          idx.name = Identifier(Constraint.autoname(idx))
        }
        holder.mapping.table.add(idx)
      }
    }
  }

  class NotNull extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      ch.columns foreach (c => c.nullable = false)
    }
  }

  /** 不可更新，不可插入 */
  class ReadOnly extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      pm.updatable = false
      pm.insertable = false
    }
  }

  /** 不可更新，但可插入 */
  class Immutable extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      pm.updatable = false
      pm.insertable = true
    }
  }

  class Lob extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
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
        val engine = holder.engine
        if (isBlob) {
          ch.columns foreach (c => c.sqlType = engine.toType(Types.BLOB))
        } else {
          ch.columns foreach (c => c.sqlType = engine.toType(Types.CLOB))
        }
      }
    }
  }

  class Unique extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      ch.columns foreach { c =>
        c.unique = true
        val table = holder.mapping.table
        table.createUniqueKey("", c.name.value)
      }
    }
  }

  class DefaultValue(v: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      ch.columns foreach { c =>
        if c.sqlType.isStringType && !v.startsWith("'") then c.defaultValue = Some("'" + v + "'")
        else c.defaultValue = holder.engine.convert(c.sqlType, v)
      }
    }
  }

  class KeyColumn(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val mp = cast(pm, holder, "key column should used on MapProperty", classOf[OrmMapProperty])
      mp.keyColumn.name = Identifier(name)
    }
  }

  class KeyLength(len: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val mp = cast(pm, holder, "key length should used on MapProperty", classOf[OrmMapProperty])
      val x = mp.keyColumn
      mp.keyColumn.sqlType = holder.engine.toType(x.sqlType.code, len)
    }
  }

  class ElementColumn(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val mp = cast(pm, holder, "element column should used on PluralProperty", classOf[OrmPluralProperty])

      mp.element match {
        case ch: OrmBasicType => ch.columns foreach (x => x.name = Identifier(name))
        case _: OrmEntityType => mp.inverseColumn foreach (x => x.name = Identifier(name))
        case _ =>
      }
    }
  }

  class ElementLength(len: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val mp = cast(pm, holder, "element length should used on PluralProperty", classOf[OrmPluralProperty])
      mp.element match {
        case ch: OrmBasicType => ch.columns foreach (x => x.sqlType = holder.engine.toType(x.sqlType.code, len))
        case _: OrmEntityType => mp.inverseColumn foreach (x => x.sqlType = holder.engine.toType(x.sqlType.code, len))
        case _ =>
      }
    }
  }

  class JoinColumn(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val mp = cast(pm, holder, "element column should be used on PluralProperty", classOf[OrmPluralProperty])
      if (null != mp.ownerColumn) {
        mp.ownerColumn.name = Identifier(name)
      }
    }
  }

  class PartitionKey extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val p = cast(pm, holder, "element should be used on SingularProperty", classOf[OrmSingularProperty])
      holder.mapping.partitionKey = Some(pm.name)
    }
  }

  class Version extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val p = cast(pm, holder, "element should be used on SingularProperty", classOf[OrmSingularProperty])
      pm.optimisticLocked = true
    }
  }

  class Cache(val cacheholder: CacheHolder) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      cacheholder.add(List(new Collection(holder.clazz, path)))
    }
  }

  private def genOwnerColumn(holder: EntityHolder[_], mappedBy: Option[String]): Column = {
    val mappings = holder.mappings
    val idType = BeanInfos.get(holder.mapping.clazz).getPropertyType("id").get
    val colName = mappedBy match {
      case Some(p) => holder.mappings.columnName(holder.mapping.clazz, p, key = true)
      case None => holder.mappings.columnName(holder.mapping.clazz, holder.mapping.entityName, key = true)
    }
    new Column(mappings.database.engine.toIdentifier(colName), mappings.sqlTypeMapping.sqlType(idType), false)
  }

  class Many2Many(mappedBy: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val colpm = cast(pm, holder, "many2many should used on seq", classOf[OrmCollectionProperty])
      colpm.mappedBy = Some(mappedBy)
      if (!colpm.element.isInstanceOf[OrmEntityType]) {
        mismatch("many2many with mappedBy should be applied on entity", holder.mapping, pm)
      }
      colpm.table = None
    }
  }

  class One2Many(targetEntity: Option[Class[_]], mappedBy: String, private var cascade: Option[String] = None) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val colpm = cast(pm, holder, "one2many should used on seq", classOf[OrmCollectionProperty])
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
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val cm = cast(pm, holder, "order by should used on seq", classOf[OrmCollectionProperty])
      cm.orderBy = Some(orderBy)
    }
  }

  class Table(table: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      cast(pm, holder, "table should used on seq", classOf[OrmPluralProperty]).table = Some(table)
    }
  }

  class ColumnName(name: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      if (ch.columns.size == 1) {
        val table = holder.mapping.table
        table.rename(ch.columns.head, Identifier(name))
      }
    }
  }

  class ColumnType(typeCode: Int, precision: Int, scale: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      if (ch.columns.size == 1) {
        val nt = holder.engine.toType(typeCode, precision, scale)
        ch.columns.head.sqlType = nt
      }
    }
  }

  class Numeric(precision: Int, scale: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      if (ch.columns.size == 1) {
        val nt = holder.engine.toType(Types.NUMERIC, precision, scale)
        ch.columns.head.sqlType = nt
      }
    }
  }

  class OrderColumn(orderColumn: String) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val collp = cast(pm, holder, "order column should used on many2many seq", classOf[OrmCollectionProperty])
      val idxCol = new Column(Identifier(if (null == orderColumn) MappingModule.OrderColumnName else orderColumn), holder.mappings.sqlTypeMapping.sqlType(classOf[Int]), false)
      idxCol.comment = Some("index no")
      collp.index = Some(idxCol)
    }
  }

  class Length(len: Int) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val ch = cast(pm, holder, "Column holder needed", classOf[ColumnHolder])
      ch.columns foreach (c => c.sqlType = holder.engine.toType(c.sqlType.code, len, c.sqlType.scale.getOrElse(0)))
    }
  }

  class Target(clazz: Class[_]) extends PropertyDeclaration {
    def apply(holder: EntityHolder[_], path: String, pm: OrmProperty): Unit = {
      val sp = pm.asInstanceOf[OrmSingularProperty]
      sp.propertyType = holder.mappings.refEntity(clazz, clazz.getName)
      sp.clazz = clazz
    }
  }


  final class EntityHolder[T](val mapping: OrmEntityType, val mappings: Mappings, val clazz: Class[T],
                              module: MappingModule) {

    def engine: Engine = mappings.database.engine

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

    /** 类型安全声明：bind[User].declare { e => e.name.first is(notnull, length(20)) }
     * e 为 scala.Dynamic 的 [[DeclareProp]]，属性路径编译期为 selectDynamic 链，无需 $Tracker 类
     */
    def declare(declarations: DeclareProp => Any): this.type = {
      declarations(new DeclareProp("", this))
      this
    }

    def generator(strategy: String): this.type = {
      mapping.idGenerator = new IdGenerator(strategy, autoConfig = false)
      if (mapping.isAbstract) { //update subclass id generator
        val clazz = mapping.clazz
        mappings.entityTypes.values foreach { et =>
          if (clazz.isAssignableFrom(et.clazz) && (null == et.idGenerator || et.idGenerator.autoConfig)) {
            et.idGenerator = mapping.idGenerator
          }
        }
      }
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

  def cast[T](pm: OrmProperty, holder: EntityHolder[_], msg: String, clazz: Class[T]): T = {
    if (!clazz.isAssignableFrom(pm.getClass)) mismatch(msg, holder.mapping, pm)
    pm.asInstanceOf[T]
  }
}

@beta
abstract class MappingModule(var name: Option[String]) extends MetaRegistrar {

  import MappingModule.*

  private var currentHolder: EntityHolder[_] = _
  private val defaultIdGenerators = Collections.newMap[Class[_], String]
  private val cacheConfig = new CacheConfig()
  private val entityMappings = Collections.newMap[String, OrmEntityType]
  private[orm] var mappings: Mappings = _
  /** 构建期标记：registering()（生成器独立实例化）置真，运行期 configure() 直接走 binding() 保持假。 */
  private[orm] var buildTime = false

  override final def registering(): Unit = {
    if (this.mappings == null) {
      this.mappings = MappingModule.buildMappings
      this.buildTime = true
    }
    binding()
  }

  init()

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

  protected def default(v: String) = new DefaultValue(v)

  protected def lob = new Lob

  protected def length(len: Int) = new Length(len)

  protected def cacheable: Cache = new Cache(new CacheHolder(mappings, cacheConfig.region, cacheConfig.usage))

  protected def cacheable(region: String, usage: String): Cache = new Cache(new CacheHolder(mappings, region, usage))

  protected def target[T](clazz: Class[T]): Target = new Target(clazz)

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

  protected def number(p: Int, s: Int): Numeric = new Numeric(p, s)

  protected def setType(code: Int, p: Int, s: Int): ColumnType = new ColumnType(code, p, s)

  protected def keyColumn(name: String): KeyColumn = new KeyColumn(name)

  protected def keyLength(len: Int): KeyLength = new KeyLength(len)

  protected def eleColumn(name: String): ElementColumn = new ElementColumn(name)

  protected def eleLength(len: Int): ElementLength = new ElementLength(len)

  protected def joinColumn(name: String): JoinColumn = new JoinColumn(name)

  protected def partitionKey: PartitionKey = new PartitionKey

  protected def version: Version = new Version

  protected inline def bind[T: ClassTag]: EntityHolder[T] = ${ MappingModule.bind[T]('{ "" }, 'this) }

  protected inline def bind[T: ClassTag](entityName: String): EntityHolder[T] = ${ MappingModule.bind[T]('entityName, 'this) }

  /** 绑定实体：通过 BeanInfos 获取 BeanInfo（优先二进制/缓存，回退运行时反射）。 */
  def bindImpl[T](cls: Class[T], entityName: String): EntityHolder[T] = {
    bindImpl(cls, entityName, BeanInfos.get(cls))
  }

  /** 绑定实体：使用指定的 BeanInfo（编译期 dig 或 JSON 加载）。 */
  def bindImpl[T](cls: Class[T], entityName: String, beanInfo: BeanInfo): EntityHolder[T] = {
    val mapping = mappings.autobind(cls, entityName, beanInfo)

    if (null == mapping.idGenerator) {
      //find superclasses id generator
      var superCls: Class[_] = cls.getSuperclass
      while (null != superCls && superCls != classOf[Object] && null == mapping.idGenerator) {
        if (entityMappings.contains(superCls.getName)) {
          val idg = entityMappings(superCls.getName).idGenerator
          if null != idg && !idg.autoConfig then mapping.idGenerator = idg
        }
        superCls = superCls.getSuperclass
      }
    }

    if (null == mapping.idGenerator) { //find id generator by id type
      beanInfo.getPropertyType("id") foreach { idtype =>
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

  protected final def collection[T](clazz: Class[T], properties: String*): List[Collection] = {
    properties.map(p => new Collection(clazz, p)).toList
  }

  protected final def defaultCache(region: String, usage: String): Unit = {
    require(!region.contains("."), "Region name cannot contains dot,replace it with -.")
    cacheConfig.region = region
    cacheConfig.usage = usage
  }

  final def configure(mappings: Mappings): Unit = {
    Logger.debug(s"Process ${getClass.getName}")
    this.mappings = mappings
    this.binding()
    entityMappings.clear()
  }

  /** 构建期 registering()/configure() 后暴露已绑定的实体类型集合
   * （供 BeangleProxyGenerator 等构建期工具读取；未注册时为空）。
   */
  def entityTypes: scala.collection.Map[String, OrmEntityType] = {
    if (null == mappings) Map.empty else mappings.entityTypes
  }

  def index(name: String, unique: Boolean, properties: Any*): Unit = {
    val holder = currentHolder
    val pms = Collections.newBuffer[OrmProperty]
    properties foreach {
      case p: Prop => pms += holder.mapping.property(p.path)
      case s: String => pms += holder.mapping.property(s)
      case other => throw new RuntimeException(s"Cannot access property of ${other.getClass.getName} in index declaration")
    }
    if (pms.isEmpty) {
      throw new RuntimeException("Cannot find access properties for " + holder.mapping.entityName + " with index declarations")
    }
    new IndexDeclaration(name, unique).apply(holder, pms)
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
