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

import java.lang.reflect.Modifier
import java.net.URL
import java.util.Locale

import org.beangle.commons.collection.Collections
import org.beangle.commons.config.Resources
import org.beangle.commons.lang.annotation.value
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.commons.lang.{ClassLoaders, Strings}
import org.beangle.commons.logging.Logging
import org.beangle.commons.text.i18n.Messages
import org.beangle.data.jdbc.meta.{Column, Database, Table}
import org.beangle.data.jdbc.{DefaultSqlTypeMapping, SqlTypeMapping}
import org.beangle.data.model.meta.Domain._
import org.beangle.data.model.meta._
import org.beangle.data.model.{IntIdEntity, LongIdEntity, ShortIdEntity, StringIdEntity}
import org.beangle.data.orm.Jpas._
import org.beangle.data.orm.cfg.Profiles

import scala.collection.mutable
import scala.reflect.runtime.{universe => ru}

object Mappings {

  case class Holder(mapping: EntityTypeMapping, meta: MutableStructType)

}

final class Mappings(val database: Database, val profiles: Profiles) extends Logging {

  def this(database: Database, ormLocations: List[URL]) {
    this(database, new Profiles(new Resources(None, ormLocations, None)))
  }

  var locale: Locale = Locale.getDefault

  var sqlTypeMapping: SqlTypeMapping = new DefaultSqlTypeMapping(database.engine)

  val entities = new mutable.HashMap[String, EntityTypeImpl]

  /** all type mappings(clazz -> Entity) */
  val classMappings = new mutable.HashMap[Class[_], EntityTypeMapping]

  /** custome types */
  val typeDefs = new mutable.HashMap[String, TypeDef]

  /** Buildin value types */
  val valueTypes = new mutable.HashSet[Class[_]]

  /** Buildin enum types */
  val enumTypes = new mutable.HashMap[String, String]

  /** Classname.property -> Collection */
  val collectMap = new mutable.HashMap[String, Collection]

  /** Only entities */
  val entityMappings: mutable.Map[String, EntityTypeMapping] = Collections.newMap[String, EntityTypeMapping]

  private var messages: Messages = _

  def collections: Iterable[Collection] = collectMap.values

  def getMapping(clazz: Class[_]): EntityTypeMapping = classMappings(clazz)

  def addMapping(mapping: EntityTypeMapping): this.type = {
    val cls = mapping.clazz
    classMappings.put(cls, mapping)
    if (!cls.isInterface && !Modifier.isAbstract(cls.getModifiers)) {
      //replace super entity with same entityName
      //It's very strange,hibnerate ClassMetadata with has same entityName and mappedClass in type overriding,
      //So, we leave  hibernate a  clean world.
      entityMappings.get(mapping.entityName) match {
        case Some(o) => if (o.clazz.isAssignableFrom(mapping.clazz)) entityMappings.put(mapping.entityName, mapping)
        case None => entityMappings.put(mapping.entityName, mapping)
      }
    } else {
      mapping.table.phantom = true
    }
    this
  }

  def addCollection(definition: Collection): this.type = {
    collectMap.put(definition.clazz.getName + definition.property, definition)
    this
  }

  def cache(em: EntityTypeMapping, region: String, usage: String): this.type = {
    em.cacheRegion = region
    em.cacheUsage = usage
    this
  }

  def cacheAll(em: EntityTypeMapping, region: String, usage: String): this.type = {
    em.cacheAll = true
    em.cacheRegion = region
    em.cacheUsage = usage
    this
  }

  def addType(name: String, clazz: String, params: Map[String, String]): Unit = {
    typeDefs.put(name, new TypeDef(clazz, params))
  }

  def autobind(): Unit = {
    messages = Messages(locale)
    profiles.modules foreach { m =>
      m.configure(this)
    }
    //superclass first,merge bingdings
    classMappings.keys.toList.sortWith { (a, b) => a.isAssignableFrom(b) } foreach (cls => merge(classMappings(cls)))

    //remove interface and abstract class binding
    val notEntities = entityMappings.filter {
      case (_, c) => c.clazz.isInterface || Modifier.isAbstract(c.clazz.getModifiers)
    }
    entityMappings --= notEntities.keys
    //remove phantom tables
    database.schemas.values foreach { s =>
      val phantomTables = s.tables.filter(_._2.phantom)
      s.tables.subtractAll(phantomTables.keys)
    }
    //create primary/foreign keys and cache
    entityMappings.values foreach (em => firstPass(em))
    entityMappings.values foreach (em => secondPass(em))
  }

  def autobind(cls: Class[_], entityName: String, typ: ru.Type): EntityTypeMapping = {
    if (cls.isAnnotationPresent(Jpas.JpaEntityAnn)) return null

    val fixedEntityName = if (entityName == null) Jpas.findEntityName(cls) else entityName
    val entity = refEntity(cls, fixedEntityName)
    val mapping = refMapping(cls, fixedEntityName)
    val mh = Mappings.Holder(mapping, entity)
    val manifest = BeanInfos.get(mapping.clazz, typ)
    manifest.readables foreach {
      case (name, prop) =>
        if (!prop.isTransient && prop.readable && prop.writable && !mapping.properties.contains(name)) {
          val optional = prop.typeinfo.optional
          val propType = prop.typeinfo.clazz
          val p =
            if (name == "id") {
              bindId(mh, name, propType, typ)
            } else if (isEntity(propType)) {
              bindManyToOne(mh, name, propType, optional)
            } else if (isSeq(propType)) {
              bindSeq(mh, name, propType, typ)
            } else if (isSet(propType)) {
              bindSet(mh, name, propType, typ)
            } else if (isMap(propType)) {
              bindMap(mh, name, propType, typ)
            } else if (isComponent(propType)) {
              bindComponent(mh, name, propType, typ)
            } else {
              bindScalar(mh, name, propType, scalarTypeName(name, propType), optional)
            }
          mapping.properties += (name -> p)
        }
    }
    mapping
  }

  def refEntity(clazz: Class[_], entityName: String): EntityTypeImpl = {
    entities.get(entityName) match {
      case Some(entity) => {
        if (entity.clazz != clazz && entity.clazz.isAssignableFrom(clazz)) entity.clazz = clazz
        entity
      }
      case None =>
        val e = new EntityTypeImpl(entityName, clazz)
        entities.put(entityName, e)
        e
    }
  }

  def refMapping(clazz: Class[_], entityName: String): EntityTypeMapping = {
    classMappings.get(clazz) match {
      case Some(m) => m
      case None =>
        val em = entityMappings.get(entityName) match {
          case Some(entity) => {
            if (entity.clazz != clazz && entity.clazz.isAssignableFrom(clazz)) {
              entity.typ.asInstanceOf[EntityTypeImpl].clazz = clazz
            }
            entity
          }
          case None =>
            val naming = profiles.getNamingPolicy(clazz).classToTableName(clazz, entityName)
            val schema = database.getOrCreateSchema(naming.schema.orNull)
            val table = schema.createTable(naming.text)
            val e = new EntityTypeMapping(refEntity(clazz, entityName), table)
            if (clazz.isInterface || Modifier.isAbstract(clazz.getModifiers)) {
              e.isAbstract = true
              table.phantom = true
            }
            entityMappings.put(entityName, e)
            e
        }
        classMappings.put(clazz, em)
        em
    }
  }

  def refToOneMapping(entityClazz: Class[_], entityName: String): BasicTypeMapping = {
    new BasicTypeMapping(new BasicType(idTypeOf(entityClazz)), newRefColumn(entityClazz, entityName))
  }

  /**
    * @param key 表示是否是一个外键
    */
  def columnName(clazz: Class[_], propertyName: String, key: Boolean = false): String = {
    val lastDot = propertyName.lastIndexOf(".")
    var colName = if (lastDot == -1) propertyName else propertyName.substring(lastDot + 1)
    colName = if (key) colName + "Id" else colName
    colName = profiles.getNamingPolicy(clazz).propertyToColumnName(clazz, colName)
    colName
  }

  /** 查找实体主键
    * */
  private def firstPass(etm: EntityTypeMapping): Unit = {
    val clazz = etm.typ.clazz
    if (null == etm.idGenerator) {
      throw new RuntimeException(s"Cannot find id generator for entity ${etm.typ.entityName}")
    }
    if (null == etm.typ.id) {
      throw new RuntimeException(s"Cannot find id for entity ${etm.typ.entityName}")
    }
    val idName = etm.typ.id.name
    val pm = etm.getPropertyMapping(idName)
    val column = pm.asInstanceOf[SingularPropertyMapping].columns.head
    column.comment = Some(getComment(clazz, idName) + (":" + etm.idGenerator.name))
    etm.table.createPrimaryKey("", column.name.toLiteral(etm.table.engine))
    etm.table.comment = Some(getComment(clazz, clazz.getSimpleName))
  }

  /** 处理外键及其关联表格,以及集合的缓存设置
    * 这些需要被引用方(各个表的主键)生成之后才能进行
    */
  private def secondPass(etm: EntityTypeMapping): Unit = {
    processPropertyMappings(etm.typ.clazz, etm.table, etm)
    processCache(etm, etm)
  }

  private def processCache(stm: StructTypeMapping, em: EntityTypeMapping): Unit = {
    if (em.cacheAll) {
      stm.properties foreach {
        case (p, pm) =>
          pm match {
            case spm: SingularPropertyMapping =>
              spm.mapping match {
                case etm: EmbeddableTypeMapping =>
                  processCache(etm, em)
                case _ =>
              }
            case ppm: PluralPropertyMapping[_] =>
              val canCache: Boolean = ppm.property match {
                case e: CollectionPropertyImpl =>
                  e.element match {
                    case et: EntityType => entityMappings(et.entityName).cacheable
                    case _ => true
                  }
                case _ =>
                  true
              }
              if (canCache) {
                addCollection(new Collection(em.clazz, p, em.cacheRegion, em.cacheUsage))
              }
          }
      }
    }
  }

  private def processPropertyMappings(clazz: Class[_], table: Table, stm: StructTypeMapping): Unit = {
    stm.properties foreach {
      case (p, pm) =>
        val property = pm.property.asInstanceOf[Property]
        pm match {
          case spm: SingularPropertyMapping =>
            spm.mapping match {
              case btm: BasicTypeMapping =>
                val column = btm.columns.head
                spm.property.propertyType match {
                  case et: EntityType =>
                    createForeignKey(table, spm.columns, entityMappings(et.entityName).table)
                    var fkcomment = getComment(clazz, property.name, null)
                    if (null == fkcomment) fkcomment = getComment(et.clazz, et.clazz.getSimpleName)
                    column.comment = Some(fkcomment + "ID")
                  case _ =>
                    if (column.comment.isEmpty) column.comment = Some(getComment(clazz, property.name))
                }
              case etm: EmbeddableTypeMapping =>
                processPropertyMappings(property.clazz, table, etm)
            }
          case ppm: PluralPropertyMapping[_] =>
            if (ppm.many2many) {
              if (ppm.table.isEmpty) ppm.table = Some(table.name.toString + "_" + Strings.unCamel(p, '_'))
              val collectTable = table.schema.createTable(ppm.table.get)
              collectTable.comment = Some(getComment(clazz, property.name))
              ppm.ownerColumn.comment = Some(getComment(clazz, clazz.getSimpleName) + "ID")
              collectTable.add(ppm.ownerColumn)
              val indexName = "i_" + collectTable.name.value
              collectTable.createIndex(indexName, false, ppm.ownerColumn.name.value)
              createForeignKey(collectTable, List(ppm.ownerColumn), table)
              ppm.element match {
                case btm: BasicTypeMapping =>
                  val elec = btm.columns.head
                  ppm.property.asInstanceOf[PluralProperty].element match {
                    case et: EntityType =>
                      createForeignKey(collectTable, btm.columns, entityMappings(et.entityName).table)
                      var fkcomment = getComment(clazz, property.name + ".element", null)
                      if (null == fkcomment) fkcomment = getComment(et.clazz, et.clazz.getSimpleName)
                      elec.comment = Some(fkcomment + "ID")
                    case _ =>
                      if (elec.comment.isEmpty) elec.comment = Some(elec.name.toString)
                  }
                  collectTable.add(elec)
                case _ =>
              }
              ppm match {
                case mm: MapPropertyMapping =>
                  mm.key match {
                    case pspm: BasicTypeMapping => collectTable.add(pspm.columns.head)
                    case _ =>
                  }
                case _ =>
              }
              collectTable.createPrimaryKey(null, collectTable.columns.map(_.name.toLiteral(table.engine)).toList: _*)
            }
        }
    }
  }


  private def createForeignKey(table: Table, columns: Iterable[Column], refTable: Table): Unit = {
    table.createForeignKey(null, columns.head.name.toLiteral(table.engine), refTable)
  }

  private def getComment(clazz: Class[_], key: String): String = {
    getComment(clazz, key, key + "?")
  }

  private def getComment(clazz: Class[_], key: String, defaults: String): String = {
    val comment = messages.get(clazz, key)
    if (key == comment) defaults else comment
  }

  /** Support features inheritence
    * <li> buildin primary type will be not null
    */
  private def merge(entity: EntityTypeMapping): Unit = {
    val cls = entity.clazz
    // search parent and interfaces
    var supclz: Class[_] = cls.getSuperclass
    val supers = new mutable.ListBuffer[EntityTypeMapping]
    cls.getInterfaces foreach (i => if (classMappings.contains(i)) supers += classMappings(i))
    while (supclz != null && supclz != classOf[Object]) {
      if (classMappings.contains(supclz)) supers += classMappings(supclz)
      supclz.getInterfaces foreach (i => if (classMappings.contains(i)) supers += classMappings(i))
      supclz = supclz.getSuperclass
    }

    val inheris = Collections.newMap[String, PropertyMapping[_]]
    supers.reverse foreach { e =>
      inheris ++= e.properties.filter(!_._2.mergeable) // filter not mergeable
      if (entity.idGenerator == null) entity.idGenerator = e.idGenerator
      if (null == entity.cacheRegion && null == entity.cacheUsage) entity.cache(e.cacheRegion, e.cacheUsage)
    }

    val inherited = Collections.newMap[String, PropertyMapping[_]]
    inheris foreach { case (name, p) =>
      if (entity.properties(name).mergeable) {
        inherited.put(name, p.copy())
      }
    }
    entity.addProperties(inherited)
  }


  private def bindComponent(mh: Mappings.Holder, name: String, propertyType: Class[_], tpe: ru.Type): SingularPropertyMapping = {
    val ct = new EmbeddableTypeImpl(propertyType)
    val cp = new SingularPropertyImpl(name, propertyType, ct)
    mh.meta.addProperty(cp)
    val cem = new EmbeddableTypeMapping(ct)
    val cpm = new SingularPropertyMapping(cp, cem)
    val ctpe = tpe.member(ru.TermName(name)).asMethod.returnType
    val manifest = BeanInfos.get(propertyType, ctpe)
    manifest.readables foreach {
      case (name, prop) =>
        if (!prop.isTransient && prop.readable && prop.writable) {
          val optional = prop.typeinfo.optional
          val propType = prop.typeinfo.clazz
          val cmh = new Mappings.Holder(mh.mapping, ct)
          val p =
            if (isEntity(propType)) {
              if (propType == mh.mapping.clazz) {
                ct.parentName = Some(name);
                null.asInstanceOf[PropertyMapping[SingularProperty]]
              } else {
                bindManyToOne(cmh, name, propType, optional)
              }
            } else if (isSeq(propType)) {
              bindSeq(cmh, name, propType, ctpe)
            } else if (isSet(propType)) {
              bindSet(cmh, name, propType, ctpe)
            } else if (isMap(propType)) {
              bindMap(cmh, name, propType, ctpe)
            } else if (isComponent(propType)) {
              bindComponent(cmh, name, propType, ctpe)
            } else {
              bindScalar(cmh, name, propType, scalarTypeName(name, propType), optional)
            }
          if (null != p) cem.properties += (name -> p)
        }
    }
    cpm
  }

  private def scalarTypeName(name: String, clazz: Class[_]): String = {
    if (clazz == classOf[Object]) {
      throw new RuntimeException("Cannot find scalar type for object")
    }
    if (clazz.isAnnotationPresent(classOf[value])) {
      valueTypes += clazz
      clazz.getName
    } else if (classOf[Enumeration#Value].isAssignableFrom(clazz)) {
      val typeName = clazz.getName
      enumTypes.put(typeName, Strings.substringBeforeLast(typeName, "$"))
      typeName
    } else {
      clazz.getName
    }
  }

  private def idTypeOf(clazz: Class[_]): Class[_] = {
    if (classOf[LongIdEntity].isAssignableFrom(clazz)) {
      classOf[Long]
    } else if (classOf[IntIdEntity].isAssignableFrom(clazz)) {
      classOf[Int]
    } else if (classOf[ShortIdEntity].isAssignableFrom(clazz)) {
      classOf[Short]
    } else if (classOf[StringIdEntity].isAssignableFrom(clazz)) {
      classOf[String]
    } else {
      BeanInfos.get(clazz).getPropertyType("id").get
    }
  }

  private def bindMap(mh: Mappings.Holder, name: String, propertyType: Class[_], tye: ru.Type): MapPropertyMapping = {

    val typeSignature = typeNameOf(tye, name)
    val kvtype = Strings.substringBetween(typeSignature, "[", "]")

    var mapKeyType = Strings.substringBefore(kvtype, ",").trim
    var mapEleType = Strings.substringAfter(kvtype, ",").trim

    var keyMeta: Type = null
    var keyMapping: TypeMapping = null

    var eleMeta: Type = null
    var eleMapping: TypeMapping = null

    val mapKeyClazz = ClassLoaders.load(mapKeyType)
    if (isEntity(mapKeyClazz)) {
      val k = refEntity(mapKeyClazz, mapKeyType)
      keyMeta = k
      val idType = idTypeOf(mapKeyClazz)
      keyMapping = new BasicTypeMapping(new BasicType(idType), newRefColumn(mapKeyClazz, mapKeyType))
    } else {
      val k = new BasicType(mapKeyClazz)
      keyMeta = k
      keyMapping = new BasicTypeMapping(k, newColumn("name", mapKeyClazz, false))
    }

    val mapEleClazz = ClassLoaders.load(mapEleType)
    if (isEntity(mapEleClazz)) {
      val e = refEntity(mapEleClazz, mapEleType)
      eleMeta = e
      val idType = idTypeOf(mapEleClazz)
      eleMapping = new BasicTypeMapping(new BasicType(idType), newRefColumn(mapEleClazz, mapEleType))
    } else {
      val e = new BasicType(mapEleClazz)
      eleMeta = e
      eleMapping = new BasicTypeMapping(e, newColumn("value", mapEleClazz, false))
    }

    val meta = new MapPropertyImpl(name, propertyType, keyMeta, eleMeta)
    mh.meta.addProperty(meta)
    val p = new MapPropertyMapping(meta, keyMapping, eleMapping)
    p.ownerColumn = newRefColumn(mh.mapping.clazz, mh.mapping.entityName)
    p
  }

  private def typeNameOf(tye: ru.Type, name: String): String = {
    tye.member(ru.TermName(name)).typeSignatureIn(tye).toString()
  }

  private def bindSeq(mh: Mappings.Holder, name: String, propertyType: Class[_], tye: ru.Type): CollectionPropertyMapping = {
    val typeSignature = typeNameOf(tye, name)
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    val entityClazz = ClassLoaders.load(entityName)

    val elem = buildElement(entityClazz, entityName)
    val meta = new CollectionPropertyImpl(name, propertyType, elem._1)
    mh.meta.addProperty(meta)

    val p = new CollectionPropertyMapping(meta, elem._2)
    //may be a many2many,so generate owner column.
    p.ownerColumn = newRefColumn(mh.mapping.clazz, mh.mapping.entityName)
    p
  }

  private def bindSet(mh: Mappings.Holder, name: String, propertyType: Class[_], tye: ru.Type): CollectionPropertyMapping = {
    val typeSignature = typeNameOf(tye, name)
    val entityName = Strings.substringBetween(typeSignature, "[", "]")
    val entityClazz = ClassLoaders.load(entityName)
    val elem = buildElement(entityClazz, entityName)
    val meta = new CollectionPropertyImpl(name, propertyType, elem._1)
    mh.meta.addProperty(meta)

    val p = new CollectionPropertyMapping(meta, elem._2)
    p.ownerColumn = newRefColumn(mh.mapping.clazz, mh.mapping.entityName)
    p
  }

  private def buildElement(entityClazz: Class[_], entityName: String): Tuple2[Type, TypeMapping] = {
    var elemType: Type = null
    var elemMapping: TypeMapping = null
    if (isEntity(entityClazz)) {
      elemType = refEntity(entityClazz, entityName)
      elemMapping = refToOneMapping(entityClazz, entityName)
    } else {
      val e = new BasicType(entityClazz)
      elemType = e
      elemMapping = new BasicTypeMapping(e, newColumn("value", entityClazz, false))
    }
    Tuple2(elemType, elemMapping)
  }

  private def bindId(mh: Mappings.Holder, name: String, propertyType: Class[_], tye: ru.Type): SingularPropertyMapping = {
    val typ = new BasicType(propertyType)
    val meta = new SingularPropertyImpl(name, propertyType, typ)
    meta.optional = false
    mh.meta.addProperty(meta)

    val column = newColumn(columnName(mh.mapping.clazz, name), propertyType, false)
    column.nullable = meta.optional
    val elemMapping = new BasicTypeMapping(typ, column)

    val p = new SingularPropertyMapping(meta, elemMapping)
    mh.mapping.table.add(column)

    p
  }

  private def bindScalar(mh: Mappings.Holder, name: String, propertyType: Class[_], typeName: String, optional: Boolean): SingularPropertyMapping = {
    val typ = new BasicType(propertyType)
    val meta = new SingularPropertyImpl(name, propertyType, typ)
    meta.optional = optional
    mh.meta.addProperty(meta)

    val column = newColumn(columnName(mh.mapping.clazz, name, false), propertyType, true)
    column.nullable = meta.optional
    val elemMapping = new BasicTypeMapping(typ, column)
    val p = new SingularPropertyMapping(meta, elemMapping)
    //FIXME
    //    if (None == p.typeName) p.typeName = Some(typeName)
    mh.mapping.table.add(column)
    p
  }

  private def bindManyToOne(mh: Mappings.Holder, name: String, propertyType: Class[_], optional: Boolean): SingularPropertyMapping = {
    val typ = refEntity(propertyType, propertyType.getName)
    val meta = new SingularPropertyImpl(name, propertyType, typ)
    meta.optional = optional
    mh.meta.addProperty(meta)

    val idType = idTypeOf(propertyType)
    val column = newColumn(columnName(mh.mapping.clazz, name, true), idType, optional)
    val p = new SingularPropertyMapping(meta, new BasicTypeMapping(new BasicType(idType), column))
    mh.mapping.table.add(column)
    p
  }

  private def newColumn(name: String, clazz: Class[_], optional: Boolean): Column = {
    new Column(database.engine.toIdentifier(name), sqlTypeMapping.sqlType(clazz), optional)
  }

  private def newRefColumn(clazz: Class[_], entityName: String): Column = {
    val idType = idTypeOf(clazz)
    new Column(database.engine.toIdentifier(columnName(clazz, entityName, true)), sqlTypeMapping.sqlType(idType), false)
  }
}
