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
import org.beangle.commons.config.Resources
import org.beangle.commons.lang.annotation.value
import org.beangle.commons.lang.reflect.TypeInfo.IterableType
import org.beangle.commons.lang.reflect.{BeanInfo, BeanInfos, TypeInfo}
import org.beangle.commons.lang.{ClassLoaders, Strings}
import org.beangle.commons.logging.Logging
import org.beangle.commons.text.i18n.Messages
import org.beangle.data.jdbc.meta.{Column, Database, Table}
import org.beangle.data.jdbc.{DefaultSqlTypeMapping, SqlTypeMapping}
import org.beangle.data.model.meta.*
import org.beangle.data.model.{IntIdEntity, LongIdEntity, ShortIdEntity, StringIdEntity}
import org.beangle.data.orm.Jpas.*
import org.beangle.data.orm.cfg.Profiles

import java.lang.reflect.Modifier
import java.net.URL
import java.util.Locale
import scala.collection.mutable

final class Mappings(val database: Database, val profiles: Profiles) extends Logging {

  def this(database: Database, ormLocations: List[URL]) = {
    this(database, new Profiles(new Resources(None, ormLocations, None)))
  }

  var locale: Locale = Locale.getDefault

  var sqlTypeMapping: SqlTypeMapping = new DefaultSqlTypeMapping(database.engine)

  /** all type mappings(clazz -> Entity) */
  val classTypes = new mutable.HashMap[Class[_], OrmEntityType]

  /** custome types */
  val typeDefs = new mutable.HashMap[String, TypeDef]

  /** Buildin value types */
  val valueTypes = new mutable.HashSet[Class[_]]

  /** Buildin enum types */
  val enumTypes = new mutable.HashSet[String]

  /** Classname.property -> Collection */
  val collectMap = new mutable.HashMap[String, Collection]

  /** Only entities */
  val entityTypes: mutable.Map[String, OrmEntityType] = Collections.newMap[String, OrmEntityType]

  private var messages: Messages = _

  def collections: Iterable[Collection] = collectMap.values

  def getEntity(clazz: Class[_]): OrmEntityType = classTypes(clazz)

  private def addEntity(mapping: OrmEntityType): this.type = {
    val cls = mapping.clazz
    classTypes.put(cls, mapping)
    if (!cls.isInterface && !Modifier.isAbstract(cls.getModifiers)) {
      //replace super entity with same entityName
      //It's very strange,hibnerate ClassMetadata has same entityName and mappedClass in type overriding,
      //So, we leave hibernate a clean world.
      entityTypes.get(mapping.entityName) match {
        case Some(o) => if (o.clazz.isAssignableFrom(mapping.clazz)) entityTypes.put(mapping.entityName, mapping)
        case None => entityTypes.put(mapping.entityName, mapping)
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

  def cache(em: OrmEntityType, region: String, usage: String): this.type = {
    em.cacheRegion = region
    em.cacheUsage = usage
    this
  }

  def cacheAll(em: OrmEntityType, region: String, usage: String): this.type = {
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
    classTypes.subtractAll(classTypes.filter(x => x._2.properties.isEmpty).keys)
    //superclass first,merge bingdings
    classTypes.keys.toList.sortWith { (a, b) => a.isAssignableFrom(b) } foreach (cls => merge(classTypes(cls)))

    //remove interface and abstract class binding
    val notEntities = entityTypes.filter {
      case (_, c) => c.clazz.isInterface || Modifier.isAbstract(c.clazz.getModifiers)
    }
    entityTypes --= notEntities.keys
    //remove phantom tables
    database.schemas.values foreach { s =>
      val phantomTables = s.tables.filter(_._2.phantom)
      s.tables.subtractAll(phantomTables.keys)
    }
    //create primary/foreign keys and cache
    entityTypes.values foreach (em => firstPass(em))
    entityTypes.values foreach (em => secondPass(em))
  }

  def autobind(cls: Class[_], entityName: String, manifest: BeanInfo): OrmEntityType = {
    if (cls.isAnnotationPresent(Jpas.JpaEntityAnn)) return null

    val fixedEntityName = if (entityName == null) Jpas.findEntityName(cls) else entityName
    val entity = refEntity(cls, fixedEntityName)
    manifest.readables foreach { case (name, prop) =>
      if (!prop.isTransient && prop.readable && prop.writable && !entity.properties.contains(name)) {
        val typeinfo = prop.typeinfo
        val propType = if typeinfo.isOptional then typeinfo.args(0).clazz else typeinfo.clazz
        if (name == "id") {
          bindId(entity, name, typeinfo)
        } else if (isEntity(propType)) {
          bindManyToOne(entity, entity, name, typeinfo)
        } else if (isSeq(propType) || isSet(propType)) {
          bindCollection(entity, entity, name, typeinfo)
        } else if (isMap(propType)) {
          bindMap(entity, entity, name, typeinfo)
        } else if (isComponent(propType)) {
          bindComponent(entity, entity, name, typeinfo)
        } else {
          bindScalar(entity, entity, name, typeinfo)
        }
      }
    }
    entity
  }

  def refEntity(clazz: Class[_], entityName: String): OrmEntityType = {
    entityTypes.get(entityName) match {
      case Some(entity) =>
        if (entity.clazz != clazz && entity.clazz.isAssignableFrom(clazz)) {
          entity.clazz = clazz
        }
        entity
      case None =>
        val naming = profiles.getNamingPolicy(clazz).classToTableName(clazz, entityName)
        val schema = database.getOrCreateSchema(naming.schema.orNull)
        val table = schema.createTable(naming.text)
        val e = new OrmEntityType(entityName, clazz, table)
        if (clazz.isInterface || Modifier.isAbstract(clazz.getModifiers)) {
          e.isAbstract = true
          table.phantom = true
        }
        addEntity(e)
        e
    }
  }

  /**
   * @param key 表示是否是一个外键
   */
  def columnName(clazz: Class[_], propertyName: String, key: Boolean = false): String = {
    val lastDot = propertyName.lastIndexOf(".")
    var colName = if (lastDot == -1) propertyName else propertyName.substring(lastDot + 1)
    colName = if (key) colName + "Id" else colName
    colName = profiles.getNamingPolicy(clazz).propertyToColumnName(clazz, colName)
    if database.engine.keywords.contains(colName) then colName + "_"
    else colName
  }

  /** 查找实体主键
   * */
  private def firstPass(etm: OrmEntityType): Unit = {
    val clazz = etm.clazz
    if (null == etm.idGenerator) {
      throw new RuntimeException(s"Cannot find id generator for entity ${etm.entityName}")
    }
    if (null == etm.id) {
      throw new RuntimeException(s"Cannot find id for entity ${etm.entityName}")
    }
    val pm = etm.id
    val idName = pm.name
    val column = pm.asInstanceOf[OrmSingularProperty].columns.head
    column.comment = Some(getComment(clazz, idName) + (":" + etm.idGenerator.strategy))
    etm.table.createPrimaryKey("", column.name.toLiteral(etm.table.engine))
    etm.table.comment = Some(getComment(clazz, clazz.getSimpleName))
    etm.table.module = etm.module
  }

  /** 处理外键及其关联表格,以及集合的缓存设置
   * 这些需要被引用方(各个表的主键)生成之后才能进行
   */
  private def secondPass(etm: OrmEntityType): Unit = {
    processProperties(etm, etm, etm.table)
    processCache(etm, etm)
  }

  private def processCache(stm: OrmStructType, em: OrmEntityType): Unit = {
    if (em.cacheAll) {
      stm.properties foreach {
        case (p, pm) =>
          pm match {
            case spm: OrmSingularProperty =>
              spm.propertyType match {
                case etm: OrmEmbeddableType => processCache(etm, em)
                case _ =>
              }
            case ppm: OrmPluralProperty =>
              val canCache =
                ppm.element match {
                  case et: EntityType => entityTypes(et.entityName).cacheable
                  case _ => true
                }
              if (canCache) {
                addCollection(new Collection(em.clazz, p, em.cacheRegion, em.cacheUsage))
              }
          }
      }
    }
  }

  /** process table
   *
   * @param oet   orm entity type
   * @param ost   entity or component
   * @param table entity table or collectionTable
   */
  private def processProperties(oet: OrmEntityType, ost: OrmStructType, table: Table): Unit = {
    ost.properties foreach {
      case (p, property) =>
        property match {
          case spm: OrmSingularProperty =>
            spm.propertyType match {
              case btm: OrmBasicType =>
                addBasicTypeMapping(ost.clazz, property.name, btm, btm.column, table)
              case btm: OrmEntityType =>
                addBasicTypeMapping(ost.clazz, property.name, btm, spm.joinColumn.get, table)
              case etm: OrmEmbeddableType =>
                processProperties(oet, etm, table)
            }
          case ppm: OrmPluralProperty =>
            if (ppm.one2many) {
              ppm.element match {
                case et: EntityType =>
                  val etm = refEntity(et.clazz, et.entityName)
                  //check mapped by
                  ppm.mappedBy foreach { mappedBy =>
                    if (!etm.properties.contains(mappedBy)) {
                      throw new RuntimeException(s"Cannot find mappedBy property $mappedBy in ${etm.entityName}")
                    }
                  }
                  //generate implicit index
                  val defined = etm.table.indexes exists (_.columns.head == ppm.ownerColumn.name)
                  if (!defined) {
                    etm.table.createIndex("", false, ppm.ownerColumn.name.value)
                  }
                case _ =>
              }
            } else if (ppm.many2many) {
              ppm.mappedBy match {
                case Some(mappedBy) =>
                  ppm.element match {
                    case oet: OrmEntityType =>
                      val refTable = refEntity(oet.clazz, oet.entityName).table
                      val table = refTable.name.toString + "_" + Strings.unCamel(mappedBy, '_')
                      ppm.table = Some(table)
                      if (BeanInfos.get(oet.clazz).getPropertyType(mappedBy).isEmpty) {
                        throw new RuntimeException(s"Cannot find ${mappedBy} in ${oet.clazz.getName}")
                      }
                      val collectTable = refTable.schema.getOrCreateTable(table)
                      collectTable.module = oet.module
                      collectTable.createIndex(null, false, ppm.ownerColumn.name.value)
                    case _ =>
                  }
                case None =>
                  if (ppm.table.isEmpty) ppm.table = Some(table.name.toString + "_" + Strings.unCamel(p, '_'))
                  val collectTable = table.schema.getOrCreateTable(ppm.table.get)
                  collectTable.module = oet.module
                  collectTable.comment = Some(getComment(ost.clazz, property.name))
                  ppm.ownerColumn.comment = Some(getComment(oet.clazz, oet.clazz.getSimpleName) + "ID")
                  collectTable.add(ppm.ownerColumn)
                  ppm.inverseColumn foreach { c => collectTable.add(c) }
                  ppm.index foreach { idxColumn =>
                    collectTable.add(idxColumn)
                  }
                  collectTable.createIndex(null, false, ppm.ownerColumn.name.value)
                  createForeignKey(collectTable, List(ppm.ownerColumn), table)

                  ppm.element match {
                    case btm: OrmBasicType =>
                      addBasicTypeMapping(ost.clazz, property.name, ppm.element, btm.column, collectTable)
                    case oet: OrmEntityType =>
                      addBasicTypeMapping(ost.clazz, property.name, ppm.element, ppm.inverseColumn.get, collectTable)
                    case etm: OrmEmbeddableType =>
                      etm.properties foreach { case (p, pm) =>
                        pm match {
                          case spm: OrmSingularProperty =>
                            spm.propertyType match {
                              case btm: OrmBasicType =>
                                addBasicTypeMapping(etm.clazz, p, btm, btm.column, collectTable)
                              case oet: OrmEntityType =>
                                val idType = idTypeOf(oet.clazz)
                                val column = newColumn(columnName(spm.clazz, spm.name, true), idType, spm.optional)
                                addBasicTypeMapping(etm.clazz, property.name, oet, column, collectTable)
                              case ost: OrmStructType =>
                                processProperties(oet, ost, collectTable)
                            }
                          case _ => throw new RuntimeException(s"Cannot support ${pm.getClass.getName} in collection")
                        }
                      }
                  }
                  ppm match {
                    case mm: OrmMapProperty =>
                      mm.key match {
                        case obt: OrmBasicType => collectTable.add(obt.column)
                        case oet: OrmEntityType => collectTable.add(mm.keyColumn)
                        case _ =>
                      }
                    case _ =>
                  }
                  collectTable.createPrimaryKey(null, collectTable.columns.map(_.name.toLiteral(table.engine)).toList: _*)
              }
            }
        }
    }
  }

  private def addRefComment(column: Column, clazz: Class[_], entityName: String): Unit = {
    column.comment = Some(getComment(clazz, clazz.getSimpleName) + "ID")
  }

  private def addBasicTypeMapping(clazz: Class[_], propertyName: String, typ: Type, elec: Column, table: Table): Unit = {
    detectValueType(typ.clazz)
    typ match {
      case et: EntityType =>
        createForeignKey(table, List(elec), entityTypes(et.entityName).table)
        if (elec.comment.isEmpty) {
          val fkcomment = getComment(clazz, propertyName)
          if (fkcomment == propertyName + "?") { //not found
            addRefComment(elec, et.clazz, et.clazz.getSimpleName)
          } else {
            elec.comment = Some(fkcomment + "ID")
          }
        }
      case _ =>
        if (elec.comment.isEmpty) elec.comment = Some(getComment(clazz, propertyName))
    }
    if (!table.columnExits(elec.name)) {
      table.add(elec)
    }
  }

  private def createForeignKey(table: Table, columns: Iterable[Column], refTable: Table): Unit = {
    table.createForeignKey(null, columns.head.name.toLiteral(table.engine), refTable)
  }

  private def getComment(clazz: Class[_], key: String): String = {
    val comment = messages.get(clazz, key)
    if (key == comment) key + "?" else comment
  }

  /** Support features inheritence
   * <li> buildin primary type will be not null
   */
  private def merge(entity: OrmEntityType): Unit = {
    val cls = entity.clazz
    // search parent and interfaces
    var supclz: Class[_] = cls.getSuperclass
    val supers = new mutable.ListBuffer[OrmEntityType]
    cls.getInterfaces foreach (i => if (classTypes.contains(i)) supers += classTypes(i))
    while (supclz != null && supclz != classOf[Object]) {
      if (classTypes.contains(supclz)) supers += classTypes(supclz)
      supclz.getInterfaces foreach (i => if (classTypes.contains(i)) supers += classTypes(i))
      supclz = supclz.getSuperclass
    }

    val inheris = Collections.newMap[String, OrmProperty]
    supers.reverse foreach { e =>
      inheris ++= e.properties.filter(!_._2.mergeable) // filter not mergeable
      if (entity.idGenerator == null) entity.idGenerator = e.idGenerator
      if (null == entity.cacheRegion && null == entity.cacheUsage) entity.cache(e.cacheRegion, e.cacheUsage)
    }

    val inherited = Collections.newMap[String, OrmProperty]
    inheris foreach { case (name, p) =>
      if (entity.properties(name).mergeable) {
        inherited.put(name, p.copy())
      }
    }
    entity.addProperties(inherited)
  }

  private def bindComponent(entity: OrmEntityType, c: OrmStructType, name: String, typeInfo: TypeInfo): Unit = {
    val propertyType = typeInfo.clazz
    val optional = typeInfo.isOptional
    val oet = new OrmEmbeddableType(propertyType)
    val cpm = new OrmSingularProperty(name, propertyType, optional, oet)
    c.addProperty(cpm)
    val manifest = BeanInfos.get(propertyType)
    manifest.readables foreach { case (name, prop) =>
      if (!prop.isTransient && prop.readable && prop.writable) {
        val typeinfo = prop.typeinfo
        val propType = if typeinfo.isOptional then typeinfo.args(0).clazz else typeinfo.clazz
        if (isEntity(propType)) {
          if (propType == entity.clazz) {
            oet.parentName = Some(name)
          } else {
            bindManyToOne(entity, oet, name, typeinfo)
          }
        } else if (isSeq(propType) || isSet(propType)) {
          bindCollection(entity, oet, name, typeinfo)
        } else if (isMap(propType)) {
          bindMap(entity, oet, name, typeinfo)
        } else if (isComponent(propType)) {
          bindComponent(entity, oet, name, typeinfo)
        } else {
          bindScalar(entity, oet, name, typeinfo)
        }
      }
    }
  }

  private def detectValueType(clazz: Class[_]): Unit = {
    if (clazz == classOf[Object]) throw new RuntimeException("Cannot find scalar type for object")
    if (clazz.isAnnotationPresent(classOf[value])) {
      valueTypes += clazz
    } else if (classOf[_root_.scala.reflect.Enum].isAssignableFrom(clazz)) {
      enumTypes += clazz.getName
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

  private def bindMap(entity: OrmEntityType, c: OrmStructType, name: String, typeInfo: TypeInfo): Unit = {
    val it = typeInfo.asInstanceOf[IterableType]
    val kvtype = it.elementType.args

    val mapKeyClazz = kvtype(0).clazz
    val mapEleClazz = kvtype(1).clazz

    var keyMeta: OrmType = null
    var keyColumn: Column = null
    if (isEntity(mapKeyClazz)) {
      keyMeta = refEntity(mapKeyClazz, mapKeyClazz.getName)
      keyColumn = newRefColumn(mapKeyClazz, mapKeyClazz.getName)
    } else {
      keyColumn = newColumn("name", mapKeyClazz, false)
      keyColumn.comment = Some("name")
      keyMeta = new OrmBasicType(mapKeyClazz, keyColumn)
    }

    val eleMeta = buildElement(mapEleClazz, mapEleClazz.getName)
    val property = new OrmMapProperty(name, typeInfo.clazz, keyMeta, eleMeta)
    c.addProperty(property)
    property.keyColumn = keyColumn
    property.ownerColumn = newRefColumn(entity.clazz, entity.entityName)
    eleMeta match {
      case oet: OrmEntityType =>
        property.inverseColumn = Some(newRefColumn(oet.clazz, oet.entityName))
      case _ =>
    }
  }

  private def bindCollection(entity: OrmEntityType, c: OrmStructType, name: String, typeInfo: TypeInfo): Unit = {
    val entityClazz = typeInfo.asInstanceOf[IterableType].elementType.clazz
    val entityName = entityClazz.getName
    val typ = buildElement(entityClazz, entityName)
    val property = new OrmCollectionProperty(name, typeInfo.clazz, typ)
    c.addProperty(property)
    //may be a many2many,so generate owner column.
    property.ownerColumn = newRefColumn(entity.clazz, entity.entityName)
    typ match {
      case _: OrmEntityType =>
        property.inverseColumn = Some(newRefColumn(entityClazz, entityName))
      case _ =>
    }
  }

  private def buildElement(clazz: Class[_], entityName: String): OrmType = {
    if (isEntity(clazz)) {
      refEntity(clazz, entityName)
    } else if (isComponent(clazz)) {
      val e = new OrmEmbeddableType(clazz)
      val manifest = BeanInfos.get(clazz)
      manifest.readables foreach { case (name, prop) =>
        if (!prop.isTransient && prop.readable && prop.writable) {
          val optional = prop.typeinfo.isOptional
          val propType = prop.typeinfo.clazz

          var property: OrmProperty = null
          if (isEntity(propType)) {
            val ormType = refEntity(propType, propType.getName)
            val idType = idTypeOf(propType)
            val column = newColumn(columnName(propType, name, true), idType, optional)
            addRefComment(column, ormType.clazz, ormType.entityName)
            val sp = new OrmSingularProperty(name, propType, optional, ormType)
            sp.joinColumn = Some(column)
            property = sp
          } else if (isComponent(propType)) {
            property = new OrmSingularProperty(name, propType, optional, buildElement(propType, null))
          } else {
            val column = newColumn(columnName(propType, name), propType, optional)
            val ormType = new OrmBasicType(propType, column)
            property = new OrmSingularProperty(name, propType, optional, ormType)
          }
          e.addProperty(property)
        }
      }
      e
    } else {
      new OrmBasicType(clazz, newColumn("value_", clazz, false))
    }
  }

  private def bindId(entity: OrmEntityType, name: String, typeinfo: TypeInfo): Unit = {
    val clazz = typeinfo.clazz
    val column = newColumn(columnName(entity.clazz, name), clazz, false)
    column.nullable = false
    val property = new OrmSingularProperty(name, clazz, false, new OrmBasicType(clazz, column))
    entity.addProperty(property)
    entity.table.add(column)
  }

  private def bindScalar(entity: OrmEntityType, c: OrmStructType, name: String, typeInfo: TypeInfo): Unit = {
    val clazz = if (typeInfo.isOptional) typeInfo.args(0).clazz else typeInfo.clazz
    detectValueType(clazz)
    val column = newColumn(columnName(c.clazz, name), clazz, typeInfo.isOptional)
    val property = new OrmSingularProperty(name, clazz, typeInfo.isOptional, new OrmBasicType(clazz, column))
    entity.table.add(column)
    c.addProperty(property)
  }

  private def bindManyToOne(entity: OrmEntityType, c: OrmStructType, name: String, typeInfo: TypeInfo): Unit = {
    val clazz = if typeInfo.isOptional then typeInfo.args(0).clazz else typeInfo.clazz
    val typ = refEntity(clazz, clazz.getName)
    val idType = idTypeOf(clazz)
    val column = newColumn(columnName(c.clazz, name, true), idType, typeInfo.isOptional)
    val property = new OrmSingularProperty(name, clazz, typeInfo.isOptional, typ)
    property.joinColumn = Some(column)
    c.addProperty(property)
    entity.table.add(column)
  }

  private def newColumn(name: String, clazz: Class[_], optional: Boolean): Column = {
    new Column(database.engine.toIdentifier(name), sqlTypeMapping.sqlType(clazz), optional)
  }

  def newRefColumn(clazz: Class[_], entityName: String): Column = {
    val idType = idTypeOf(clazz)
    val column = new Column(database.engine.toIdentifier(columnName(clazz, entityName, true)), sqlTypeMapping.sqlType(idType), false)
    addRefComment(column, clazz, entityName)
    column
  }
}
