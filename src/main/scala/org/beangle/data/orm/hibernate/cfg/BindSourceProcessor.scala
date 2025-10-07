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

package org.beangle.data.orm.hibernate.cfg

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.model.meta.{BasicType, EntityType}
import org.beangle.data.orm.*
import org.beangle.data.orm.hibernate.id.*
import org.beangle.data.orm.hibernate.udt.*
import org.beangle.data.orm.hibernate.{ScalaPropertyAccessStrategy, ScalaPropertyAccessor}
import org.beangle.jdbc.meta.{Column, SqlType}
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.naming.{Identifier, ObjectNameNormalizer}
import org.hibernate.boot.model.source.spi.MetadataSourceProcessor
import org.hibernate.boot.model.{IdentifierGeneratorDefinition, TypeDefinition}
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.registry.selector.spi.StrategySelector
import org.hibernate.boot.spi.{InFlightMetadataCollector, MetadataBuildingContext, SecondPass}
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.id.PersistentIdentifierGenerator.{CATALOG, IDENTIFIER_NORMALIZER, SCHEMA}
import org.hibernate.mapping.Collection.{DEFAULT_ELEMENT_COLUMN_NAME, DEFAULT_KEY_COLUMN_NAME}
import org.hibernate.mapping.IndexedCollection.DEFAULT_INDEX_COLUMN_NAME
import org.hibernate.mapping.{Backref, BasicValue, DependantValue, IndexBackref, KeyValue, PersistentClass, PrimaryKey, RootClass, SimpleValue, ToOne, Value, Bag as HBag, Collection as HCollection, Column as HColumn, Component as HComponent, Fetchable as HFetchable, List as HList, ManyToOne as HManyToOne, Map as HMap, OneToMany as HOneToMany, Property as HProperty, Set as HSet}
import org.hibernate.property.access.spi.PropertyAccessStrategy
import org.hibernate.{FetchMode, MappingException}

import java.lang.reflect.Modifier
import java.util as ju

/** Beangle Model Bind Metadata processor.
 *
 * @see org.hibernate.boot.model.source.internal.hbm.ModelBinder
 */
class BindSourceProcessor(mappings: Mappings, metadataSources: MetadataSources, context: MetadataBuildingContext) extends MetadataSourceProcessor {

  private val metadata: InFlightMetadataCollector = context.getMetadataCollector

  private val additionalIdGenerators = Map(
    IdGenerator.AutoIncrement -> classOf[AutoIncrementGenerator].getName,
    IdGenerator.Date -> classOf[DateStyleGenerator].getName,
    IdGenerator.DateTime -> classOf[DateTimeStyleGenerator].getName,
    IdGenerator.Code -> classOf[CodeStyleGenerator].getName
  )

  private val objectNameNormalizer = new ObjectNameNormalizer() {
    protected override def getBuildingContext: MetadataBuildingContext = {
      context
    }
  }

  private var minColumnEnableDynaUpdate = 7

  private var globalIdGenerator: IdGenerator = _

  override def processQueryRenames(): Unit = {}

  override def processNamedQueries(): Unit = {}

  override def processAuxiliaryDatabaseObjectDefinitions(): Unit = {}

  override def processFilterDefinitions(): Unit = {}

  override def processFetchProfiles(): Unit = {}

  override def prepareForEntityHierarchyProcessing(): Unit = {}

  override def postProcessEntityHierarchies(): Unit = {}

  override def processResultSetMappings(): Unit = {}

  override def finishUp(): Unit = {}

  override def prepare(): Unit = {
    val strategySelector = metadataSources.getServiceRegistry.getService(classOf[StrategySelector])
    strategySelector.registerStrategyImplementor(classOf[PropertyAccessStrategy], "scala", classOf[ScalaPropertyAccessStrategy])
  }

  /**
   * Process all custom Type definitions.  This step has no
   * prerequisites.
   */
  override def processTypeDefinitions(): Unit = {
    val cls = context.getBuildingOptions.getServiceRegistry.getService(classOf[ClassLoaderService])
    mappings.typeDefs foreach {
      case (m, t) =>
        val p = new ju.HashMap[String, String]
        t.params foreach (e => p.put(e._1, e._2))
        val definition = new TypeDefinition(m, cls.classForName(t.clazz), null, p)
        metadata.getTypeDefinitionRegistry.register(definition)
    }
  }

  override def processIdentifierGenerators(): Unit = {
    // 注册缺省的sequence生成器
    additionalIdGenerators foreach { case (name, strategy) =>
      metadata.addDefaultIdentifierGenerator(new IdentifierGeneratorDefinition(name, strategy))
    }
  }

  override def processEntityHierarchies(processedEntityNames: java.util.Set[String]): Unit = {
    for ((_, definition) <- mappings.entityTypes) {
      val rc = bindClass(definition)

      if (null != definition.cacheUsage) {
        val region = if (null == definition.cacheRegion) definition.entityName else definition.cacheRegion
        rc.setCacheConcurrencyStrategy(definition.cacheUsage)
        rc.setCacheRegionName(region)
        rc.setLazyPropertiesCacheable(true)
        rc.setCached(true)
      }
    }

    for (definition <- mappings.collections if (null != definition.cacheUsage)) {
      val role = mappings.getEntity(definition.clazz).entityName + "." + definition.property
      val region = if (null == definition.cacheRegion) role else definition.cacheRegion
      val cb = metadata.getCollectionBinding(role)
      cb.setCacheConcurrencyStrategy(definition.cacheUsage)
      cb.setCacheRegionName(region)
    }
  }

  private class CollSecondPass(context: MetadataBuildingContext, collection: HCollection, colp: OrmCollectionProperty) extends SecondPass {

    override def doSecondPass(entities: java.util.Map[String, PersistentClass]): Unit = {
      bindCollectionSecondPass(colp, collection, entities)
      collection.createAllKeys()
    }
  }

  private class MapSecondPass(context: MetadataBuildingContext, map: HMap, mapp: OrmMapProperty) extends SecondPass {
    override def doSecondPass(entities: java.util.Map[String, PersistentClass]): Unit = {
      bindMapSecondPass(mapp, map, entities)
      map.createAllKeys()
    }
  }

  private def bindClass(em: OrmEntityType): RootClass = {
    val entity = new RootClass(context)
    entity.setOptimisticLockStyle(OptimisticLockStyle.valueOf(em.optimisticLockStyle))
    entity.setEntityName(em.entityName)
    entity.setJpaEntityName(em.entityName)

    entity.setAbstract(Modifier.isAbstract(em.clazz.getModifiers))
    entity.setLazy(em.isLazy)
    entity.setClassName(em.clazz.getName)
    if (null != em.proxy) {
      entity.setProxyInterfaceName(em.proxy)
      entity.setLazy(true)
    } else if (entity.isLazy) {
      entity.setProxyInterfaceName(em.clazz.getName)
    }

    val table = metadata.addTable(em.table.schema.name.value, null, em.table.name.value, null, em.isAbstract, context)
    entity.setTable(table)
    val ahead = Collections.newSet[String]
    ahead.addOne(em.id.name)
    // bind id
    bindSimpleId(em, entity, em.id.name, em.id.asInstanceOf[OrmSingularProperty])
    // bind partition key
    em.partitionKey foreach { partKey =>
      val bv = new BasicValue(context, table)
      bv.setPartitionKey(true)
      val osp = em.properties(partKey).asInstanceOf[OrmSingularProperty]
      val value = bindSimpleValue(bv, partKey, osp, osp.propertyType.asInstanceOf[OrmBasicType].clazz.getName)
      val property = createProperty(value, partKey, entity.getMappedClass, osp)
      entity.addProperty(property)
      ahead.addOne(partKey)
    }
    //set primary key
    em.table.primaryKey foreach { primaryKey =>
      val pk = new PrimaryKey(table)
      pk.setName(primaryKey.name.toString)
      primaryKey.columns foreach { c =>
        pk.addColumn(table.getColumn(Identifier.toIdentifier(c.toString)))
      }
      table.setPrimaryKey(pk)
    }

    em.properties.filter(x => !ahead.contains(x._1)) foreach { case (propertyName, p) =>
      val value: Value = p match {
        case spm: OrmSingularProperty =>
          spm.propertyType match {
            case et: EntityType =>
              bindManyToOne(new HManyToOne(context, table), propertyName, et.entityName, spm.joinColumns, spm)
            case btm: OrmBasicType =>
              val bv = new BasicValue(context, table)
              bindSimpleValue(bv, propertyName, spm, btm.clazz.getName)
            case etm: OrmEmbeddableType =>
              val subpath = qualify(em.entityName, propertyName)
              bindComponent(new HComponent(context, entity), etm, subpath, false)
          }
        case colp: OrmPluralProperty =>
          val hcol = createCollection(colp, entity)
          metadata.addCollectionBinding(bindCollection(entity, em.entityName + "." + propertyName, colp, hcol))
          hcol
      }
      val property = createProperty(value, propertyName, entity.getMappedClass, p)
      entity.addProperty(property)
    }

    // trigger dynamic update
    if (!entity.useDynamicUpdate && entity.getTable.getColumnSpan >= minColumnEnableDynaUpdate) {
      entity.setDynamicUpdate(true)
    }

    assert(null != entity.getIdentifier, s"${entity.getEntityName} requires identifier.")
    metadata.addEntityBinding(entity)
    entity
  }

  private def bindSimpleId(em: OrmEntityType, entity: RootClass, idName: String, idp: OrmSingularProperty): Unit = {
    val id = new BasicValue(context, entity.getTable)
    entity.setIdentifier(id)
    id.setTypeName(idp.propertyType.clazz.getName)
    bindColumns(idp.columns, id, idName)
    val prop = new HProperty
    prop.setValue(id)
    bindProperty(idName, idp, prop)
    entity.setIdentifierProperty(prop)
    entity.setDeclaredIdentifierProperty(prop)
    makeIdentifier(em, id)
  }

  private def bindCollectionSecondPass(colp: OrmPluralProperty, collection: HCollection,
                                       entities: java.util.Map[String, PersistentClass]): Unit = {
    colp.element match {
      case et: EntityType =>
        if (colp.one2many) {
          val oneToMany = collection.getElement.asInstanceOf[HOneToMany]
          val assocClass = oneToMany.getReferencedEntityName
          val entity = entities.get(assocClass)
          if (entity == null) throw new MappingException("Association references unmapped class: " + assocClass)
          oneToMany.setAssociatedClass(entity)
          collection.setCollectionTable(entity.getTable)
          collection.setInverse(true)
        } else {
          val element = bindManyToOne(new HManyToOne(context, collection.getCollectionTable), DEFAULT_ELEMENT_COLUMN_NAME,
            et.entityName, colp.inverseColumn.toSeq)
          collection.setElement(element)
          //        bindManyToManySubelements( collection, subnode )
        }
      case _ =>
        colp.element match {
          case compositeElem: OrmEmbeddableType =>
            val element = new HComponent(context, collection)
            collection.setElement(element)
            bindComponent(element, compositeElem, collection.getRole + ".element", false)
          case e: OrmBasicType =>
            val elt = new BasicValue(context, collection.getCollectionTable)
            collection.setElement(elt)
            bindSimpleValue(elt, DEFAULT_ELEMENT_COLUMN_NAME, e, e.clazz.getName)
        }
    }

    val keyElem = new SimpleColumn(colp.ownerColumn)
    val propRef = collection.getReferencedPropertyName
    val keyVal =
      if (propRef == null) collection.getOwner.getIdentifier
      else collection.getOwner.getRecursiveProperty(propRef).getValue.asInstanceOf[KeyValue]

    val key = new DependantValue(context, collection.getCollectionTable, keyVal)
    key.disableForeignKey()
    key.setUpdateable(true)
    key.setOnDeleteAction(OnDeleteAction.NO_ACTION)
    bindSimpleValue(key, DEFAULT_KEY_COLUMN_NAME, keyElem, collection.getOwner.getIdentifier.getType.getName)
    collection.setKey(key)

    colp.index foreach { idx =>
      val index = new SimpleColumn(idx)
      val list = collection.asInstanceOf[HList]
      val iv = bindSimpleValue(new BasicValue(context, collection.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, index, "integer")
      list.setIndex(iv)
    }

    if (collection.isOneToMany
      && !collection.isInverse
      && !collection.getKey.isNullable) {
      val entityName = collection.getElement.asInstanceOf[HOneToMany].getReferencedEntityName
      val referenced = metadata.getEntityBinding(entityName)
      val prop = new Backref
      prop.setName("_" + collection.getOwnerEntityName + "." + colp.name + "Backref")
      prop.setUpdateable(false)
      prop.setSelectable(false)
      prop.setCollectionRole(collection.getRole)
      prop.setEntityName(collection.getOwner.getEntityName)
      prop.setValue(collection.getKey)
      referenced.addProperty(prop)
    }
  }

  private def bindMapSecondPass(mapp: OrmMapProperty, map: HMap, entities: java.util.Map[String, PersistentClass]): Unit = {
    bindCollectionSecondPass(mapp, map, entities)

    mapp.key match {
      case bt: BasicType =>
        map.setIndex(bindSimpleValue(new BasicValue(context, map.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME,
          new SimpleColumn(mapp.keyColumn), bt.clazz.getName))
      case kt: EntityType =>
        map.setIndex(bindManyToOne(new HManyToOne(context, map.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, kt.entityName, Seq(mapp.keyColumn)))
      case ck: OrmEmbeddableType =>
        map.setIndex(bindComponent(new HComponent(context, map), ck, map.getRole + ".index", map.isOneToMany))
    }

    if (map.isOneToMany && !map.getKey.isNullable && !map.isInverse) {
      val entityName = map.getElement.asInstanceOf[HOneToMany].getReferencedEntityName
      val referenced = metadata.getEntityBinding(entityName)
      val ib = new IndexBackref
      ib.setName("_" + map.getOwnerEntityName + "." + mapp.name + "IndexBackref")
      ib.setUpdateable(false)
      ib.setSelectable(false)
      ib.setCollectionRole(map.getRole)
      ib.setEntityName(map.getOwner.getEntityName)
      ib.setValue(map.getIndex)
      referenced.addProperty(ib)
    }
  }

  private def bindSimpleValue(value: SimpleValue, name: String, colHolder: ColumnHolder, typeName: String): SimpleValue = {
    val td = metadata.getTypeDefinition(typeName)
    if (null != td) {
      value.setTypeName(td.getTypeImplementorClass.getName)
      value.setTypeParameters(td.getParameters)
    } else {
      if (typeName.equals("[B")) {
        value.setTypeName("binary")
      } else {
        value.setTypeName(typeName)
      }
    }
    //hibernate use a custom sqlType for instant type.
    if (typeName == "java.time.Instant") {
      colHolder.columns foreach (c => c.sqlType = c.sqlType.copy(code = org.hibernate.`type`.SqlTypes.TIMESTAMP_UTC))
    }
    bindColumns(colHolder.columns, value, name)
    value
  }

  private def nameColumn(cm: Column, propertyPath: String): (Identifier, String) = {
    val db = metadata.getDatabase
    val logicalName = if (null == cm.name) db.toIdentifier(propertyPath) else db.toIdentifier(cm.name.value)
    (logicalName, logicalName.render(db.getDialect))
  }

  private def bindColumns(cms: Iterable[Column], simpleValue: SimpleValue, propertyPath: String): Unit = {
    val table = simpleValue.getTable
    var count = 0
    for (cm <- cms) {
      val column = new HColumn
      column.setValue(simpleValue)
      column.setTypeIndex(count)
      count += 1
      bindColumn(simpleValue, cm, column)
      val names = nameColumn(cm, propertyPath)
      val logicalName = names._1
      val physicalName = names._2
      column.setName(physicalName)
      if (table != null) {
        table.addColumn(column)
        metadata.addColumnNameBinding(table, logicalName, column)
      }
      simpleValue.addColumn(column)
    }
  }

  private def bindColumn(value: SimpleValue, cm: Column, column: HColumn): Unit = {
    val sqlType = cm.sqlType
    column.setSqlTypeCode(sqlType.code)
    if sqlType.isNumberType then column.setPrecision(sqlType.precision.getOrElse(0))
    else column.setLength(sqlType.precision.getOrElse(0))

    column.setNullable(cm.nullable)
    val scale = sqlType.scale.getOrElse(0)
    if (scale > 0) {
      //如果是浮点类型，不要设置精度，hibernate只支持BigDecimal类型上设置scala
      if (value.getTypeName == "double" || value.getTypeName == "float") {
        column.setSqlType(cm.sqlType.name)
      } else {
        column.setScale(scale)
      }
    }
    //hibernate not need know column unique
    //column.setUnique(cm.unique)
    cm.defaultValue foreach (v => column.setDefaultValue(v))

  }

  private def bindManyToOne(manyToOne: HManyToOne, name: String, entityName: String, cols: Seq[Column], fetchable: Fetchable = null): HManyToOne = {
    bindColumns(cols, manyToOne, name)
    if (null != fetchable) initOuterJoinFetchSetting(manyToOne, fetchable)
    manyToOne.setReferencedEntityName(entityName)
    manyToOne.setReferenceToPrimaryKey(true)
    manyToOne.setLazy(true)
    manyToOne.disableForeignKey() //hibernate no need to know foreign key constraints。

    mappings.entityTypes.get(entityName).foreach { et =>
      manyToOne.setTypeName(et.id.clazz.getName)
    }
    manyToOne
  }

  private def initOuterJoinFetchSetting(col: HFetchable, seqp: Fetchable): Unit = {
    seqp.fetch match {
      case Some(fetch) => col.setFetchMode(if ("join" == fetch) FetchMode.JOIN else FetchMode.SELECT)
      case None => col.setFetchMode(FetchMode.DEFAULT)
    }
    col.setLazy(true)
  }

  private def makeIdentifier(em: OrmEntityType, sv: SimpleValue): Unit = {
    if (null == globalIdGenerator && em.idGenerator == null) throw new RuntimeException("Cannot find id generator for entity " + em.entityName)
    val generator = if (null != globalIdGenerator) globalIdGenerator else em.idGenerator
    val strategy = additionalIdGenerators.getOrElse(generator.strategy, generator.strategy)
    sv.setIdentifierGeneratorStrategy(strategy)
    val params = new ju.HashMap[String, Object]
    params.put(IDENTIFIER_NORMALIZER, objectNameNormalizer)

    val database = metadata.getDatabase
    val name = database.getDefaultNamespace.getPhysicalName
    if (null != name && null != name.getSchema) {
      params.put(SCHEMA, database.getDefaultNamespace.getPhysicalName.getSchema.render(database.getDialect))
    }
    if (null != name && null != name.getCatalog) {
      params.put(CATALOG, database.getDefaultNamespace.getPhysicalName.getCatalog.render(database.getDialect))
    }
    generator.params foreach {
      case (k, v) => params.put(k, v)
    }
    sv.setIdentifierGeneratorParameters(params)
    generator.nullValue match {
      case Some(v) => sv.setNullValue(v)
      case None => sv.setNullValue(if "assigned" == sv.getIdentifierGeneratorStrategy then "undefined" else null)
    }
    //val gd = new IdentifierGeneratorDefinition(strategy, strategy, params)
    //GeneratorBinder.createGeneratorFrom(gd, sv, context)
  }

  private def qualify(first: String, second: String): String = {
    s"$first.$second"
  }

  private def createCollection(colp: OrmPluralProperty, owner: PersistentClass): HCollection = {
    colp match {
      case _: OrmMapProperty => new HMap(context, owner)
      case cp: OrmCollectionProperty =>
        if (Jpas.isSeq(cp.clazz)) {
          if (cp.index.isEmpty) new HBag(context, owner) else new HList(context, owner)
        } else {
          new HSet(context, owner)
        }
    }
  }

  private def bindComponent(component: HComponent, comp: OrmEmbeddableType, path: String, isEmbedded: Boolean): HComponent = {
    component.setEmbedded(isEmbedded)
    component.setRoleName(path)

    component.setComponentClassName(comp.clazz.getName)
    if (isEmbedded) {
      if (component.getOwner.hasPojoRepresentation) component.setComponentClassName(component.getOwner.getClassName)
      else component.setDynamic(true)
    }
    comp.parentName foreach (pp => component.setParentProperty(pp))

    comp.properties foreach {
      case (propertyName, p) =>
        var value: Value = null
        val subpath = path + "." + propertyName
        val relativePath =
          if (isEmbedded) propertyName
          else subpath.substring(component.getOwner.getEntityName.length + 1)

        p match {
          case colp: OrmPluralProperty =>
            val hcol = createCollection(colp, component.getOwner)
            metadata.addCollectionBinding(bindCollection(component.getOwner, subpath, colp, hcol))
            value = hcol
          case sm: OrmSingularProperty =>
            sm.propertyType match {
              case btm: OrmBasicType =>
                value = bindSimpleValue(new BasicValue(context, component.getTable), relativePath, sm, btm.clazz.getName)
              case et: EntityType =>
                value = bindManyToOne(new HManyToOne(context, component.getTable), propertyName, et.entityName, sm.joinColumns, sm)
              case etm: OrmEmbeddableType =>
                value = bindComponent(new HComponent(context, component), etm, subpath, isEmbedded)
            }
        }
        if (value != null) {
          val property = createProperty(value, propertyName, ClassLoaders.load(component.getComponentClassName), p)
          component.addProperty(property)
        }
    }
    component
  }

  private def setPluralTypeName(p: OrmPluralProperty, coll: HCollection): Unit = {
    if (classOf[collection.Set[_]].isAssignableFrom(p.clazz)) {
      coll.setTypeName(classOf[SetType].getName)
    } else if (classOf[collection.Seq[_]].isAssignableFrom(p.clazz)) {
      if coll.isInstanceOf[HBag] then
        coll.setTypeName(classOf[BagType].getName)
      else
        coll.setTypeName(classOf[SeqType].getName)
    } else if (classOf[collection.Map[_, _]].isAssignableFrom(p.clazz)) {
      coll.setTypeName(classOf[MapType].getName)
    }
  }

  private def bindCollection(entity: PersistentClass, role: String, cp: OrmPluralProperty, coll: HCollection): HCollection = {
    coll.setRole(role)
    coll.setInverse(cp.inverse)
    cp.where foreach (v => coll.setWhere(v))
    cp.batchSize foreach (v => coll.setBatchSize(v))

    setPluralTypeName(cp, coll)
    initOuterJoinFetchSetting(coll, cp)
    if (cp.fetch.contains("subselect")) {
      coll.setSubselectLoadable(true)
      coll.getOwner.setSubselectLoadableCollections(true)
    }
    coll.setLazy(true)
    cp.element match {
      case et: EntityType if cp.one2many =>
        val oneToMany = new HOneToMany(context, coll.getOwner)
        coll.setElement(oneToMany)
        oneToMany.setReferencedEntityName(et.entityName)
      case _ =>
        val tableName = cp.table.get
        val table = metadata.addTable(coll.getOwner.getTable.getSchema, null, tableName, cp.subselect.orNull, false, context)
        coll.setCollectionTable(table)
    }

    cp.sort match {
      case None => coll.setSorted(false)
      case Some(sort) => coll.setSorted(true); if (sort != "natural") coll.setComparatorClassName(sort)
    }

    cp match {
      case cp: OrmCollectionProperty =>
        cp.orderBy foreach (v => coll.setOrderBy(v))
        metadata.addSecondPass(new CollSecondPass(context, coll, cp))
      case mapp: OrmMapProperty =>
        metadata.addSecondPass(new MapSecondPass(context, coll.asInstanceOf[HMap], mapp))
    }

    cp.cascade foreach (cascade => if (cascade.contains("delete-orphan")) coll.setOrphanDelete(true))
    coll
  }

  private def createProperty(value: Value, propertyName: String, clazz: Class[_], pm: OrmProperty): HProperty = {
    value match {
      case toOne: ToOne =>
        val propertyRef = toOne.getReferencedPropertyName
        if (propertyRef != null) metadata.addUniquePropertyReference(toOne.getReferencedEntityName, propertyRef)
      case coll: HCollection =>
        val propertyRef = coll.getReferencedPropertyName
        if (propertyRef != null) metadata.addPropertyReference(coll.getOwnerEntityName, propertyRef)
      case _ =>
    }

    value.createForeignKey()
    val prop = new HProperty
    prop.setValue(value)
    bindProperty(propertyName, pm, prop)
    prop
  }

  private def bindProperty(propertyName: String, pm: OrmProperty, property: HProperty): Unit = {
    property.setName(propertyName)
    property.setPropertyAccessorName(ScalaPropertyAccessor.name)
    // val cascade = pm.cascade.getOrElse(context.getBuildingOptions.getMappingDefaults.getImplicitCascadeStyleName)
    val cascade = pm.cascade.getOrElse(context.getMappingDefaults.getImplicitCascadeStyleName)
    property.setCascade(cascade)
    property.setUpdateable(pm.updatable)
    property.setInsertable(pm.insertable)
    property.setOptional(pm.optional)
    property.setOptimisticLocked(pm.optimisticLocked)
    property.setLazy(pm.isLazy)
  }
}
