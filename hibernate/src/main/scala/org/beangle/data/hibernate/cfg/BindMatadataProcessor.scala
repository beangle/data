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
package org.beangle.data.hibernate.cfg

import java.lang.reflect.Modifier
import java.time.YearMonth
import java.{ util => ju }

import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.data.hibernate.ScalaPropertyAccessStrategy
import org.beangle.data.hibernate.id.{ AutoIncrementGenerator, CodeStyleGenerator, DateStyleGenerator, DateTimeStyleGenerator, SeqPerTableStyleGenerator }
import org.beangle.data.hibernate.udt.{ EnumType, MapType, SeqType, SetType, ValueType, YearMonthType }
import org.beangle.data.jdbc.meta.Column
import org.beangle.data.model.meta.{ BasicType, EntityType, PluralProperty, Property }
import org.beangle.data.orm.{ BasicTypeMapping, CollectionPropertyMapping, ColumnHolder, EmbeddableTypeMapping, EntityTypeMapping, Fetchable, IdGenerator, Jpas, MapPropertyMapping, PluralPropertyMapping, PropertyMapping, SimpleColumn, SingularPropertyMapping, TypeDef }
import org.hibernate.{ FetchMode, MappingException }
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.model.TypeDefinition
import org.hibernate.boot.model.naming.{ Identifier, ObjectNameNormalizer }
import org.hibernate.boot.model.source.spi.MetadataSourceProcessor
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.boot.registry.selector.spi.StrategySelector
import org.hibernate.boot.spi.{ InFlightMetadataCollector, MetadataBuildingContext }
import org.hibernate.cfg.CollectionSecondPass
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, IDENTIFIER_NORMALIZER, SCHEMA }
import org.hibernate.id.factory.spi.MutableIdentifierGeneratorFactory
import org.hibernate.mapping.{ Backref, Bag => HBag, Collection => HCollection }
import org.hibernate.mapping.{ Column => HColumn, Component => HComponent, DependantValue, Fetchable => HFetchable, IndexBackref }
import org.hibernate.mapping.{ KeyValue, List => HList, ManyToOne => HManyToOne, Map => HMap, OneToMany => HOneToMany, PersistentClass, Property => HProperty, RootClass, Set => HSet, SimpleValue, ToOne, Value }
import org.hibernate.mapping.Collection.{ DEFAULT_ELEMENT_COLUMN_NAME, DEFAULT_KEY_COLUMN_NAME }
import org.hibernate.mapping.IndexedCollection.DEFAULT_INDEX_COLUMN_NAME
import org.hibernate.property.access.spi.PropertyAccessStrategy
import org.hibernate.tuple.{ GeneratedValueGeneration, GenerationTiming }

/**
 * Hibernate Bind Metadadta processor.
 * @see org.hibernate.boot.model.source.internal.hbm.ModelBinder
 */
class BindMatadataProcessor(metadataSources: MetadataSources, context: MetadataBuildingContext) extends MetadataSourceProcessor {

  private val metadata: InFlightMetadataCollector = context.getMetadataCollector

  private val mappings = metadataSources.getServiceRegistry.getService(classOf[MappingService]).mappings

  private val objectNameNormalizer = new ObjectNameNormalizer() {
    protected override def getBuildingContext(): MetadataBuildingContext = {
      context
    }
  }

  private var minColumnEnableDynaUpdate = 7

  private var globalIdGenerator: IdGenerator = _

  override def processQueryRenames() {}

  override def processNamedQueries() {}

  override def processAuxiliaryDatabaseObjectDefinitions() {}

  override def processFilterDefinitions() {}

  override def processFetchProfiles() {}

  override def prepareForEntityHierarchyProcessing() {}

  override def postProcessEntityHierarchies() {}

  override def processResultSetMappings() {}

  override def finishUp() {}

  override def prepare() {
    val strategySelector = metadataSources.getServiceRegistry.getService(classOf[StrategySelector])
    strategySelector.registerStrategyImplementor(classOf[PropertyAccessStrategy], "scala", classOf[ScalaPropertyAccessStrategy])
  }

  /**
   * Process all custom Type definitions.  This step has no
   * prerequisites.
   */
  override def processTypeDefinitions() {
    val cls = context.getBuildingOptions().getServiceRegistry().getService(classOf[ClassLoaderService])

    Map(
      (classOf[YearMonth].getName, classOf[YearMonthType])) foreach {
        case (name, clazz) =>
          val p = new ju.HashMap[String, String]
          val definition = new TypeDefinition(name, clazz, Array(name), p)
          context.getMetadataCollector.addTypeDefinition(definition)
      }

    val types = new collection.mutable.HashMap[String, TypeDef]
    types ++= mappings.typeDefs
    mappings.valueTypes foreach (t => types += (t.getName -> new TypeDef(classOf[ValueType].getName, Map("valueClass" -> t.getName))))
    mappings.enumTypes foreach (t => types += (t._1 -> new TypeDef(classOf[EnumType].getName, Map("enumClass" -> t._2))))

    types foreach {
      case (m, t) =>
        val p = new ju.HashMap[String, String]
        t.params foreach (e => p.put(e._1, e._2))
        val definition = new TypeDefinition(m, cls.classForName(t.clazz), null, p)
        context.getMetadataCollector.addTypeDefinition(definition)
    }
  }

  override def processIdentifierGenerators() {
    val identifierFactory = metadata.getIdentifierGeneratorFactory.asInstanceOf[MutableIdentifierGeneratorFactory]
    // 注册缺省的sequence生成器
    identifierFactory.register(IdGenerator.SeqPerTable, classOf[SeqPerTableStyleGenerator])
    identifierFactory.register(IdGenerator.AutoIncrement, classOf[AutoIncrementGenerator])
    identifierFactory.register(IdGenerator.Date, classOf[DateStyleGenerator])
    identifierFactory.register(IdGenerator.DateTime, classOf[DateTimeStyleGenerator])
    identifierFactory.register(IdGenerator.Code, classOf[CodeStyleGenerator])
  }

  override def processEntityHierarchies(processedEntityNames: java.util.Set[String]) {
    for ((name, definition) <- mappings.entityMappings) {
      val clazz = definition.clazz
      val rc = bindClass(definition)

      if (null != definition.cacheUsage) {
        val region = if (null == definition.cacheRegion) definition.entityName else definition.cacheRegion
        rc.setCacheConcurrencyStrategy(definition.cacheUsage)
        rc.setCacheRegionName(region)
        rc.setLazyPropertiesCacheable(true)
      }
    }

    for (definition <- mappings.collections if (null != definition.cacheUsage)) {
      val role = mappings.getMapping(definition.clazz).entityName + "." + definition.property
      val region = if (null == definition.cacheRegion) role else definition.cacheRegion
      val cb = metadata.getCollectionBinding(role)
      cb.setCacheConcurrencyStrategy(definition.cacheUsage)
      cb.setCacheRegionName(region)
    }
  }

  class CollSecondPass(context: MetadataBuildingContext, collection: HCollection, colp: CollectionPropertyMapping)
    extends CollectionSecondPass(context, collection, new java.util.HashMap[String, String]) {

    def secondPass(entities: java.util.Map[_, _], inheritedMetas: java.util.Map[_, _]): Unit = {
      bindCollectionSecondPass(colp, collection, entities.asInstanceOf[java.util.Map[String, PersistentClass]])
      collection.createAllKeys()
    }
  }

  class MapSecondPass(context: MetadataBuildingContext, map: HMap, mapp: MapPropertyMapping)
    extends CollectionSecondPass(context, map, new java.util.HashMap[String, String]) {
    override def secondPass(entities: java.util.Map[_, _], inheritedMetas: java.util.Map[_, _]): Unit = {
      bindMapSecondPass(mapp, map, entities.asInstanceOf[java.util.Map[String, PersistentClass]])
      map.createAllKeys()
    }
  }

  def bindClass(em: EntityTypeMapping): RootClass = {
    val entity = new RootClass(context)
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

    val table = metadata.addTable(em.table.schema.name.value, null, em.table.name.value, null, em.isAbstract)
    entity.setTable(table)
    em.properties foreach {
      case (propertyName, p) =>
        var value: Value = null
        p match {
          case spm: SingularPropertyMapping =>
            spm.mapping match {
              case btm: BasicTypeMapping =>
                spm.property.propertyType match {
                  case et: EntityType =>
                    value = bindManyToOne(new HManyToOne(metadata, table), propertyName, et.entityName, btm.columns, spm)
                  case _ =>
                    if (spm.property.name == "id") {
                      bindSimpleId(em, entity, propertyName, spm)
                      entity.createPrimaryKey
                    } else {
                      value = bindSimpleValue(new SimpleValue(metadata, table), propertyName, spm, btm.typ.clazz.getName)
                    }
                }
              case etm: EmbeddableTypeMapping =>
                val subpath = qualify(em.entityName, propertyName)
                value = new HComponent(metadata, entity)
                bindComponent(value.asInstanceOf[HComponent], etm, subpath, false)
            }
          case colp: PluralPropertyMapping[_] =>
            val hcol = createCollection(colp, entity)
            metadata.addCollectionBinding(bindCollection(entity, em.entityName + "." + propertyName, colp, hcol))
            value = hcol
        }

        if (value != null) {
          val property = createProperty(value, propertyName, entity.getMappedClass, p)
          entity.addProperty(property)
        }
    }

    // trigger dynamic update
    if (!entity.useDynamicUpdate && entity.getTable().getColumnSpan() >= minColumnEnableDynaUpdate) {
      entity.setDynamicUpdate(true)
    }
    assert(null != entity.getIdentifier, s"${entity.getEntityName} requires identifier.")
    metadata.addEntityBinding(entity)
    entity
  }

  private def bindSimpleId(em: EntityTypeMapping, entity: RootClass, idName: String, idp: SingularPropertyMapping): Unit = {
    val id = new SimpleValue(metadata, entity.getTable)
    entity.setIdentifier(id)
    bindColumns(idp.columns, id, idName)
    setTypeUsingReflection(id, entity.getMappedClass, idName)
    val prop = new HProperty
    prop.setValue(id)
    bindProperty(idName, idp, prop)
    entity.setIdentifierProperty(prop)
    entity.setDeclaredIdentifierProperty(prop)
    makeIdentifier(em, id)
  }

  def bindCollectionSecondPass(colp: PluralPropertyMapping[_], collection: HCollection,
                               entities: java.util.Map[String, PersistentClass]): Unit = {
    val pp = colp.property.asInstanceOf[PluralProperty]
    pp.element match {
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
          val element = bindManyToOne(new HManyToOne(metadata, collection.getCollectionTable), DEFAULT_ELEMENT_COLUMN_NAME,
            et.entityName, colp.element.asInstanceOf[BasicTypeMapping].columns)
          collection.setElement(element)
          //        bindManyToManySubelements( collection, subnode )
        }
      case _ =>
        colp.element match {
          case compositeElem: EmbeddableTypeMapping =>
            val element = new HComponent(metadata, collection)
            collection.setElement(element)
            bindComponent(element, compositeElem, collection.getRole + ".element", false)
          case e: BasicTypeMapping =>
            val elt = new SimpleValue(metadata, collection.getCollectionTable)
            collection.setElement(elt)
            bindSimpleValue(elt, DEFAULT_ELEMENT_COLUMN_NAME, e, e.typ.clazz.getName)
        }
    }

    val keyElem = new SimpleColumn(colp.ownerColumn)
    val propRef = collection.getReferencedPropertyName
    val keyVal =
      if (propRef == null) collection.getOwner.getIdentifier
      else collection.getOwner.getRecursiveProperty(propRef).getValue.asInstanceOf[KeyValue]

    val key = new DependantValue(metadata, collection.getCollectionTable, keyVal)
    key.setCascadeDeleteEnabled(false)
    bindSimpleValue(key, DEFAULT_KEY_COLUMN_NAME, keyElem, collection.getOwner.getIdentifier.getType.getName)
    collection.setKey(key)

    colp.index foreach { idx =>
      val index = new SimpleColumn(idx)
      val list = collection.asInstanceOf[HList]
      val iv = bindSimpleValue(new SimpleValue(metadata, collection.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, index, "integer")
      list.setIndex(iv)
    }

    if (collection.isOneToMany
      && !collection.isInverse
      && !collection.getKey.isNullable) {
      val entityName = collection.getElement.asInstanceOf[HOneToMany].getReferencedEntityName
      val referenced = metadata.getEntityBinding(entityName)
      val prop = new Backref
      prop.setName('_' + collection.getOwnerEntityName + "." + colp.property.asInstanceOf[Property].name + "Backref")
      prop.setUpdateable(false)
      prop.setSelectable(false)
      prop.setCollectionRole(collection.getRole)
      prop.setEntityName(collection.getOwner.getEntityName)
      prop.setValue(collection.getKey)
      referenced.addProperty(prop)
    }
  }

  def bindMapSecondPass(mapp: MapPropertyMapping, map: HMap, entities: java.util.Map[String, PersistentClass]): Unit = {
    bindCollectionSecondPass(mapp, map, entities)

    mapp.key match {
      case sk: BasicTypeMapping =>
        mapp.property.key match {
          case bt: BasicType =>
            map.setIndex(bindSimpleValue(new SimpleValue(metadata, map.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, sk, bt.clazz.getName))
          case et: EntityType =>
            val kt = mapp.property.key.asInstanceOf[EntityType]
            map.setIndex(bindManyToOne(
              new HManyToOne(metadata, map.getCollectionTable),
              DEFAULT_INDEX_COLUMN_NAME, kt.entityName, sk.columns))
        }
      case ck: EmbeddableTypeMapping =>
        map.setIndex(bindComponent(new HComponent(metadata, map), ck, map.getRole + ".index", map.isOneToMany))
    }

    if (map.isOneToMany && !map.getKey.isNullable && !map.isInverse) {
      val entityName = map.getElement.asInstanceOf[HOneToMany].getReferencedEntityName
      val referenced = metadata.getEntityBinding(entityName)
      val ib = new IndexBackref
      ib.setName('_' + map.getOwnerEntityName + "." + mapp.property.name + "IndexBackref")
      ib.setUpdateable(false)
      ib.setSelectable(false)
      ib.setCollectionRole(map.getRole)
      ib.setEntityName(map.getOwner.getEntityName)
      ib.setValue(map.getIndex)
      referenced.addProperty(ib)
    }
  }

  private def bindSimpleValue(value: SimpleValue, name: String, colHolder: ColumnHolder, typeName: String): SimpleValue = {
    if (null != typeName) {
      val td = metadata.getTypeDefinition(typeName)
      if (null != td) {
        value.setTypeName(td.getTypeImplementorClass.getName)
        value.setTypeParameters(td.getParametersAsProperties)
      } else {
        if (typeName.equals("[B")) {
          value.setTypeName("binary")
        } else {
          value.setTypeName(typeName)
        }
      }
    }
    bindColumns(colHolder.columns, value, name)
    value
  }

  private def nameColumn(cm: Column, propertyPath: String): Tuple2[Identifier, String] = {
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
      bindColumn(cm, column)
      val names = nameColumn(cm, propertyPath)
      val logicalName = names._1
      val physicalName = names._2
      column.setName(physicalName)
      if (table != null) {
        table.addColumn(column)
        metadata.asInstanceOf[InFlightMetadataCollector].addColumnNameBinding(table, logicalName, column)
      }
      simpleValue.addColumn(column)
    }
  }

  def bindColumn(cm: Column, column: HColumn) {
    val sqlType = cm.sqlType
    column.setSqlTypeCode(sqlType.code)
    column.setLength(sqlType.length.getOrElse(0))
    column.setPrecision(sqlType.precision.getOrElse(0))
    column.setScale(sqlType.scale.getOrElse(0))
    column.setNullable(cm.nullable)
    column.setUnique(cm.unique)
    cm.defaultValue foreach (v => column.setDefaultValue(v))
  }

  def bindManyToOne(manyToOne: HManyToOne, name: String, entityName: String, cols: Iterable[Column], fetchable: Fetchable = null): HManyToOne = {
    bindColumns(cols, manyToOne, name)
    if (null != fetchable) initOuterJoinFetchSetting(manyToOne, fetchable)
    manyToOne.setReferencedEntityName(entityName)
    manyToOne.setReferenceToPrimaryKey(true)
    manyToOne.setLazy(true)
    mappings.entities.get(entityName).foreach { et =>
      manyToOne.setTypeName(et.id.clazz.getName)
    }
    manyToOne
  }

  def initOuterJoinFetchSetting(col: HFetchable, seqp: Fetchable): Unit = {
    seqp.fetch match {
      case Some(fetch) => col.setFetchMode(if ("join" == fetch) FetchMode.JOIN else FetchMode.SELECT)
      case None        => col.setFetchMode(FetchMode.DEFAULT)
    }
    col.setLazy(true)
  }

  def makeIdentifier(em: EntityTypeMapping, sv: SimpleValue): Unit = {
    if (null == globalIdGenerator && em.idGenerator == null) throw new RuntimeException("Cannot find id generator for entity " + em.entityName)
    val idgenerator = if (null != globalIdGenerator) globalIdGenerator else em.idGenerator
    sv.setIdentifierGeneratorStrategy(idgenerator.name)
    val params = new ju.Properties
    params.put(IDENTIFIER_NORMALIZER, objectNameNormalizer)

    val name = metadata.getDatabase.getDefaultNamespace.getPhysicalName
    val database = metadata.getDatabase
    if (null != name && null != name.getSchema) {
      params.setProperty(SCHEMA, database.getDefaultNamespace().getPhysicalName.getSchema.render(database.getDialect))
    }
    if (null != name && null != name.getCatalog) {
      params.setProperty(CATALOG, database.getDefaultNamespace().getPhysicalName.getCatalog.render(database.getDialect))
    }

    idgenerator.params foreach {
      case (k, v) => params.setProperty(k, v)
    }
    sv.setIdentifierGeneratorProperties(params)
    sv.getTable.setIdentifierValue(sv)
    idgenerator.nullValue match {
      case Some(v) => sv.setNullValue(v)
      case None    => sv.setNullValue(if ("assigned" == sv.getIdentifierGeneratorStrategy) "undefined" else null)
    }
  }

  def qualify(first: String, second: String): String = {
    s"$first.$second"
  }

  def setTypeUsingReflection(value: Value, clazz: Class[_], propertyName: String): Unit = {
    value match {
      case sv: SimpleValue =>
        if (null == sv.getTypeName) BeanInfos.get(clazz).getPropertyType(propertyName) foreach (clz => sv.setTypeName(clz.getName))
      case _ =>
    }
  }

  def createCollection(colp: PluralPropertyMapping[_], owner: PersistentClass): HCollection = {
    colp match {
      case mapp: MapPropertyMapping => new HMap(metadata, owner)
      case cp: CollectionPropertyMapping =>
        if (Jpas.isSeq(cp.property.clazz)) {
          if (cp.index.isEmpty) new HBag(metadata, owner) else new HList(metadata, owner)
        } else {
          new HSet(metadata, owner)
        }
    }
  }

  def bindComponent(component: HComponent, comp: EmbeddableTypeMapping, path: String, isEmbedded: Boolean): HComponent = {
    component.setEmbedded(isEmbedded)
    component.setRoleName(path)

    component.setComponentClassName(comp.typ.clazz.getName)
    if (isEmbedded) {
      if (component.getOwner.hasPojoRepresentation) component.setComponentClassName(component.getOwner.getClassName)
      else component.setDynamic(true)
    }
    comp.typ.parentName foreach (pp => component.setParentProperty(pp))

    comp.properties foreach {
      case (propertyName, p) =>
        var value: Value = null
        val subpath = path + "." + propertyName
        val relativePath =
          if (isEmbedded) propertyName
          else subpath.substring(component.getOwner.getEntityName.length + 1)

        p match {
          case colp: PluralPropertyMapping[_] =>
            val hcol = createCollection(colp, component.getOwner)
            metadata.addCollectionBinding(bindCollection(component.getOwner, subpath, colp, hcol))
            value = hcol
          case sm: SingularPropertyMapping =>
            sm.mapping match {
              case btm: BasicTypeMapping =>
                sm.property.propertyType match {
                  case et: EntityType =>
                    value = bindManyToOne(new HManyToOne(metadata, component.getTable), propertyName, et.entityName, sm.columns, sm)
                  case _ =>
                    value = bindSimpleValue(new SimpleValue(metadata, component.getTable), relativePath, sm, sm.property.propertyType.clazz.getName)
                }
              case etm: EmbeddableTypeMapping =>
                value = new HComponent(metadata, component)
                bindComponent(value.asInstanceOf[HComponent], etm, subpath, isEmbedded)
            }
        }
        if (value != null) {
          val property = createProperty(value, propertyName, ClassLoaders.load(component.getComponentClassName), p)
          component.addProperty(property)
        }
    }
    component
  }

  private def setPluralTypeName(pm: PluralPropertyMapping[_], coll: HCollection): Unit = {
    val p = pm.property.asInstanceOf[PluralProperty]
    if (classOf[collection.Set[_]].isAssignableFrom(p.clazz)) {
      coll.setTypeName(classOf[SetType].getName)
    } else if (classOf[collection.Seq[_]].isAssignableFrom(p.clazz)) {
      coll.setTypeName(classOf[SeqType].getName)
    } else if (classOf[collection.Map[_, _]].isAssignableFrom(p.clazz)) {
      coll.setTypeName(classOf[MapType].getName)
    }
  }

  def bindCollection(entity: PersistentClass, role: String, cp: PluralPropertyMapping[_], coll: HCollection): HCollection = {
    coll.setRole(role)
    coll.setInverse(cp.inverse)
    cp.where foreach (v => coll.setWhere(v))
    cp.batchSize foreach (v => coll.setBatchSize(v))

    setPluralTypeName(cp, coll)
    initOuterJoinFetchSetting(coll, cp)
    if (Some("subselect") == cp.fetch) {
      coll.setSubselectLoadable(true)
      coll.getOwner.setSubselectLoadableCollections(true)
    }
    coll.setLazy(true)
    cp.property.asInstanceOf[PluralProperty].element match {
      case et: EntityType if cp.one2many =>
        val oneToMany = new HOneToMany(metadata, coll.getOwner)
        coll.setElement(oneToMany)
        oneToMany.setReferencedEntityName(et.entityName)
      case _ =>
        val tableName = cp.table.get
        val table = metadata.addTable(coll.getOwner.getTable.getSchema, null, tableName, cp.subselect.orNull, false)
        coll.setCollectionTable(table)
    }

    cp.sort match {
      case None       => coll.setSorted(false)
      case Some(sort) => coll.setSorted(true); if (sort != "natural") coll.setComparatorClassName(sort)
    }

    cp match {
      case cp: CollectionPropertyMapping =>
        cp.property.orderBy foreach (v => coll.setOrderBy(v))
        metadata.addSecondPass(new CollSecondPass(context, coll, cp))
      case mapp: MapPropertyMapping =>
        metadata.addSecondPass(new MapSecondPass(context, coll.asInstanceOf[HMap], mapp))
    }

    cp.cascade foreach (cascade => if (cascade.contains("delete-orphan")) coll.setOrphanDelete(true))
    coll
  }

  def createProperty(value: Value, propertyName: String, clazz: Class[_], pm: PropertyMapping[_]): HProperty = {
    setTypeUsingReflection(value, clazz, propertyName)
    value match {
      case toOne: ToOne =>
        val propertyRef = toOne.getReferencedPropertyName
        if (propertyRef != null) metadata.addUniquePropertyReference(toOne.getReferencedEntityName, propertyRef)
      case coll: HCollection =>
        val propertyRef = coll.getReferencedPropertyName
        if (propertyRef != null) metadata.addPropertyReference(coll.getOwnerEntityName, propertyRef)
      case _ =>
    }

    value.createForeignKey
    val prop = new HProperty
    prop.setValue(value)
    bindProperty(propertyName, pm, prop)
    prop
  }

  def bindProperty(propertyName: String, pm: PropertyMapping[_], property: HProperty): Unit = {
    property.setName(propertyName)
    //property.setPropertyAccessorName(pm.access.getOrElse(context.getMappingDefaults.getImplicitPropertyAccessorName))
    property.setPropertyAccessorName("scala")
    property.setCascade(pm.cascade.getOrElse(context.getMappingDefaults.getImplicitCascadeStyleName))
    property.setUpdateable(pm.updateable)
    property.setInsertable(pm.insertable)
    property.setOptimisticLocked(pm.optimisticLocked)
    pm.generated foreach { v =>
      val generationTiming = GenerationTiming.parseFromName(v)
      property.setValueGenerationStrategy(new GeneratedValueGeneration(generationTiming))
      if (pm.insertable) throw new MappingException(s"both insertable and generated finded for property: $propertyName")
      if (pm.updateable && generationTiming == GenerationTiming.ALWAYS) throw new MappingException(s"both updateable and generated finded for property: $propertyName")
    }
    property.setLazy(pm.lazyed)
  }
}
