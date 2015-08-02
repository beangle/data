package org.beangle.data.jpa.hibernate.cfg

import java.{ util => ju }
import java.lang.reflect.Modifier
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.reflect.BeanManifest
import org.beangle.data.model.bind.Binder.{ CollectionProperty, Column, ColumnHolder, Component, ComponentProperty, CompositeElement, CompositeKey, Element, Entity, Fetchable, IdProperty, ToManyElement, ManyToOneKey, ManyToOneProperty, MapProperty, Property => PropertyConfig, ScalarProperty, SeqProperty, SetProperty, SimpleElement, SimpleKey, TypeNameHolder }
import org.hibernate.{ FetchMode, MappingException }
import org.hibernate.cfg.{ CollectionSecondPass, Mappings }
import org.hibernate.id.PersistentIdentifierGenerator.{ CATALOG, IDENTIFIER_NORMALIZER, SCHEMA }
import org.hibernate.mapping.{ Backref, Bag => HBag, Collection }
import org.hibernate.mapping.{ Column => HColumn, Component => HComponent, DependantValue, Fetchable => HFetchable, IndexBackref }
import org.hibernate.mapping.{ KeyValue, List => HList, ManyToOne => HManyToOne, Map => HMap, OneToMany => HOneToMany, PersistentClass, Property, RootClass, Set => HSet, SimpleValue, ToOne, Value }
import org.hibernate.mapping.Collection.{ DEFAULT_ELEMENT_COLUMN_NAME, DEFAULT_KEY_COLUMN_NAME }
import org.hibernate.mapping.IndexedCollection.DEFAULT_INDEX_COLUMN_NAME
import org.hibernate.tuple.{ GeneratedValueGeneration, GenerationTiming }

object HbmConfigBinder {

  class CollSecondPass(colp: CollectionProperty, mappings: Mappings, collection: Collection)
    extends CollectionSecondPass(mappings, collection, new java.util.HashMap[String, String]) {

    def secondPass(entities: java.util.Map[_, _], inheritedMetas: java.util.Map[_, _]): Unit = {
      bindCollectionSecondPass(colp, collection, entities.asInstanceOf[java.util.Map[String, PersistentClass]], mappings)
    }
  }

  class MapSecondPass(mapp: MapProperty, mappings: Mappings, map: HMap)
    extends CollSecondPass(mapp, mappings, map) {
    override def secondPass(entities: java.util.Map[_, _], inheritedMetas: java.util.Map[_, _]): Unit = {
      bindMapSecondPass(mapp, map, entities.asInstanceOf[java.util.Map[String, PersistentClass]], mappings)
    }
  }

  def bindCollectionSecondPass(colp: CollectionProperty, collection: Collection,
    entities: java.util.Map[String, PersistentClass], mappings: Mappings): Unit = {

    colp.element foreach { ele =>
      ele match {
        case o2m: ToManyElement if (o2m.one2many) =>
          val oneToMany = collection.getElement.asInstanceOf[HOneToMany]
          val assocClass = oneToMany.getReferencedEntityName
          val entity = entities.get(assocClass)
          if (entity == null) throw new MappingException("Association references unmapped class: " + assocClass)
          oneToMany.setAssociatedClass(entity)
          collection.setCollectionTable(entity.getTable)

          collection.setInverse(true)
        case m2m: ToManyElement if (m2m.many2many) =>
          val element = bindManyToOne(new HManyToOne(mappings, collection.getCollectionTable), Collection.DEFAULT_ELEMENT_COLUMN_NAME, m2m.entityName, m2m.columns)
          collection.setElement(element)
        //        bindManyToManySubelements( collection, subnode, mappings )
        case compositeElem: CompositeElement =>
          val element = new HComponent(mappings, collection)
          collection.setElement(element)
          bindComponent(element, compositeElem, collection.getRole + ".element", false, mappings)
        case e: SimpleElement =>
          val elt = new SimpleValue(mappings, collection.getCollectionTable)
          collection.setElement(elt)
          bindSimpleValue(elt, DEFAULT_ELEMENT_COLUMN_NAME, e, e)
      }
    }

    colp.key foreach { k =>
      val keyElem = k.asInstanceOf[SimpleKey]
      val propRef = collection.getReferencedPropertyName
      val keyVal =
        if (propRef == null) collection.getOwner.getIdentifier
        else collection.getOwner.getRecursiveProperty(propRef).getValue.asInstanceOf[KeyValue]

      val key = new DependantValue(mappings, collection.getCollectionTable, keyVal)
      key.setCascadeDeleteEnabled(false)
      bindSimpleValue(key, DEFAULT_KEY_COLUMN_NAME, keyElem, keyElem)
      collection.setKey(key)
    }

    colp.index foreach { index =>
      val list = collection.asInstanceOf[HList]
      val iv = bindSimpleValue(new SimpleValue(mappings, collection.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, index, index)
      iv.setTypeName("integer")
      list.setIndex(iv)
    }

    if (collection.isOneToMany
      && !collection.isInverse
      && !collection.getKey.isNullable) {
      val entityName = collection.getElement.asInstanceOf[HOneToMany].getReferencedEntityName
      val referenced = mappings.getClass(entityName)
      val prop = new Backref
      prop.setName('_' + collection.getOwnerEntityName + "." + colp.name + "Backref")
      prop.setUpdateable(false)
      prop.setSelectable(false)
      prop.setCollectionRole(collection.getRole)
      prop.setEntityName(collection.getOwner.getEntityName)
      prop.setValue(collection.getKey)
      referenced.addProperty(prop)
    }
  }

  def bindMapSecondPass(mapp: MapProperty, map: HMap, entities: java.util.Map[String, PersistentClass], mappings: Mappings): Unit = {
    bindCollectionSecondPass(mapp, map, entities, mappings)

    mapp.mapKey match {
      case sk: SimpleKey =>
        map.setIndex(bindSimpleValue(new SimpleValue(mappings, map.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, sk, sk))
      case ck: CompositeKey =>
        map.setIndex(bindComponent(new HComponent(mappings, map), ck, map.getRole + ".index", map.isOneToMany, mappings))
      case ck: ManyToOneKey =>
        map.setIndex(bindManyToOne(new HManyToOne(mappings, map.getCollectionTable), DEFAULT_INDEX_COLUMN_NAME, ck.entityName, ck.columns))
    }

    if (map.isOneToMany && !map.getKey.isNullable && !map.isInverse) {
      val entityName = map.getElement.asInstanceOf[HOneToMany].getReferencedEntityName
      val referenced = mappings.getClass(entityName)
      val ib = new IndexBackref
      ib.setName('_' + map.getOwnerEntityName + "." + mapp.name + "IndexBackref")
      ib.setUpdateable(false)
      ib.setSelectable(false)
      ib.setCollectionRole(map.getRole)
      ib.setEntityName(map.getOwner.getEntityName)
      ib.setValue(map.getIndex)
      referenced.addProperty(ib)
    }
  }

  private def bindSimpleValue(value: SimpleValue, name: String, colHolder: ColumnHolder, typeHolder: TypeNameHolder): SimpleValue = {
    typeHolder.typeName foreach { typeName =>
      val typeDef = value.getMappings.getTypeDef(typeName)
      if (null == typeDef) {
        value.setTypeName(typeName)
      } else {
        value.setTypeName(typeDef.getTypeClass)
        value.setTypeParameters(typeDef.getParameters)
      }
    }
    bindColumns(colHolder.columns, value, name)
    value
  }

  private def bindColumns(cms: Seq[Column], simpleValue: SimpleValue, propertyPath: String): Unit = {
    val table = simpleValue.getTable
    val mappings = simpleValue.getMappings
    var count = 0
    for (cm <- cms) {
      val column = new HColumn
      column.setValue(simpleValue)
      column.setTypeIndex(count)
      count += 1
      bindColumn(cm, column)
      var columnName = cm.name
      val stategy = mappings.getNamingStrategy
      if (null == columnName) columnName = stategy.propertyToColumnName(propertyPath)
      else if (columnName.charAt(0) == '@') {
        columnName = stategy.propertyToColumnName(columnName.substring(1))
      }
      val logicalColumnName = stategy.logicalColumnName(columnName, propertyPath)
      column.setName(quoteIdentifier(stategy.columnName(columnName)))
      if (table != null) {
        table.addColumn(column)
        mappings.addColumnBinding(logicalColumnName, column, table)
      }
      simpleValue.addColumn(column)
      //          bindIndex( columnElement.attribute( "index" ), table, column, mappings )
      //          bindIndex( node.attribute( "index" ), table, column, mappings )
      //          bindUniqueKey( columnElement.attribute( "unique-key" ), table, column, mappings )
      //          bindUniqueKey( node.attribute( "unique-key" ), table, column, mappings )
    }
  }

  def bindColumn(cm: Column, column: HColumn) {
    cm.length foreach (v => column.setLength(v))

    cm.scale foreach (v => column.setScale(v))
    cm.precision foreach (v => column.setPrecision(v))
    column.setNullable(cm.nullable)
    column.setUnique(cm.unique)
    cm.defaultValue foreach (v => column.setDefaultValue(v))
    cm.sqlType foreach (v => column.setSqlType(v))
  }

  def bindManyToOne(manyToOne: HManyToOne, name: String, entityName: String, cols: Seq[Column], fetchable: Fetchable = null): HManyToOne = {
    bindColumns(cols, manyToOne, name)
    if (null != fetchable) initOuterJoinFetchSetting(manyToOne, fetchable)
    manyToOne.setReferencedEntityName(entityName)
    manyToOne.setReferenceToPrimaryKey(true)
    manyToOne
  }

  def initOuterJoinFetchSetting(col: HFetchable, seqp: Fetchable): Unit = {
    seqp.fetch match {
      case Some(fetch) => col.setFetchMode(if ("join" == fetch) FetchMode.JOIN else FetchMode.SELECT)
      case None        => col.setFetchMode(FetchMode.DEFAULT)
    }
    col.setLazy(false)
  }

  def makeIdentifier(em: Entity, sv: SimpleValue): Unit = {
    val idgenerator = em.idGenerator.get
    val mappings = sv.getMappings
    sv.setIdentifierGeneratorStrategy(idgenerator.generator)
    val params = new ju.Properties
    val normalizer = mappings.getObjectNameNormalizer
    params.put(IDENTIFIER_NORMALIZER, normalizer)

    if (mappings.getSchemaName != null) params.setProperty(SCHEMA, normalizer.normalizeIdentifierQuoting(mappings.getSchemaName))
    if (mappings.getCatalogName != null) params.setProperty(CATALOG, normalizer.normalizeIdentifierQuoting(mappings.getCatalogName))

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

  def quote(name: String): String = {
    if (name == null || name.length == 0 || isQuoted(name)) name
    else new StringBuffer(name.length + 2).append('`').append(name).append('`').toString
  }

  def isQuoted(name: String): Boolean = {
    return name != null && name.length != 0 && name.charAt(0) == '`' && name.charAt(name.length - 1) == '`'
  }

  def quoteIdentifier(identifier: String): String = {
    identifier
  }

  def unqualify(name: String): String = {
    val lastDotIdx = name.lastIndexOf(".")
    if (lastDotIdx == -1) name else name.substring(lastDotIdx + 1)
  }

  def qualify(first: String, second: String): String = {
    s"$first.$second"
  }

  def setTypeUsingReflection(value: Value, clazz: Class[_], propertyName: String): Unit = {
    value match {
      case sv: SimpleValue =>
        if (null == sv.getTypeName) BeanManifest.get(clazz).getPropertyType(propertyName) foreach (clz => sv.setTypeName(clz.getName))
      case _ =>
    }
  }

  def createCollection(colp: CollectionProperty, owner: PersistentClass, mappings: Mappings): Collection = {
    colp match {
      case seqp: SeqProperty =>
        if (null != seqp.index) new HList(mappings, owner)
        else new HBag(mappings, owner)
      case setp: SetProperty =>
        new HSet(mappings, owner)
      case mapp: MapProperty =>
        new HMap(mappings, owner)
    }
  }

  def bindComponent(component: HComponent, comp: Component, path: String, isEmbedded: Boolean, mappings: Mappings): HComponent = {
    component.setEmbedded(isEmbedded)
    component.setRoleName(path)

    comp.clazz foreach { clz =>
      component.setComponentClassName(clz)
    }
    if (isEmbedded) {
      if (component.getOwner.hasPojoRepresentation) {
        component.setComponentClassName(component.getOwner.getClassName)
      } else {
        component.setDynamic(true)
      }
    }
    comp.properties foreach {
      case (propertyName, p) =>
        var value: Value = null
        val subpath = path + "." + propertyName
        val relativePath =
          if (isEmbedded) propertyName
          else subpath.substring(component.getOwner.getEntityName.length + 1)
        p match {
          case m21: ManyToOneProperty =>
            value = bindManyToOne(new HManyToOne(mappings, component.getTable), m21.name, m21.targetEntity, m21.columns, m21)
          case colp: CollectionProperty =>
            val hcol = createCollection(colp, component.getOwner, mappings)
            mappings.addCollection(bindCollection(component.getOwner, subpath, colp, hcol))
            value = hcol
          case sp: ScalarProperty =>
            value = bindSimpleValue(new SimpleValue(mappings, component.getTable), relativePath, sp, sp)
          case cp: ComponentProperty =>
            value = new HComponent(mappings, component)
            bindComponent(value.asInstanceOf[HComponent], cp, subpath, isEmbedded, mappings)
        }
        //      else if ( "parent".equals( name ) ) {
        //        component.setParentProperty( propertyName )
        //      }
        //
        if (value != null) {
          val property = createProperty(value, propertyName, ClassLoaders.loadClass(component.getComponentClassName), p, mappings)
          component.addProperty(property)
        }
    }
    comp match {
      case cp: ComponentProperty =>
        val iter = component.getColumnIterator
        val cols = new java.util.ArrayList[HColumn]
        while (iter.hasNext) {
          cols.add(iter.next.asInstanceOf[HColumn])
        }
        component.getOwner.getTable.createUniqueKey(cols)
      case _ =>
    }
    component
  }

  def bindCollection(entity: PersistentClass, role: String, seqp: CollectionProperty, coll: Collection): Collection = {
    coll.setRole(role)
    coll.setInverse(seqp.inverse)
    val mappings = coll.getMappings
    seqp.orderBy foreach (v => coll.setOrderBy(v))
    seqp.where foreach (v => coll.setWhere(v))
    seqp.batchSize foreach (v => coll.setBatchSize(v))

    seqp.typeName foreach { typeName =>
      val typeDef = mappings.getTypeDef(typeName)
      if (typeDef != null) {
        coll.setTypeName(typeDef.getTypeClass)
        coll.setTypeParameters(typeDef.getParameters)
      } else {
        coll.setTypeName(typeName)
      }
    }

    initOuterJoinFetchSetting(coll, seqp)
    if (Some("subselect") == seqp.fetch) {
      coll.setSubselectLoadable(true)
      coll.getOwner.setSubselectLoadableCollections(true)
    }
    coll.setLazy(true)

    seqp.element foreach { ele =>
      ele match {
        case o2m: ToManyElement if o2m.one2many =>
          val oneToMany = new HOneToMany(mappings, coll.getOwner)
          coll.setElement(oneToMany)
          oneToMany.setReferencedEntityName(o2m.entityName)
        case ele: Element =>
          var tableName = seqp.table match {
            case Some(t) => mappings.getNamingStrategy.tableName(t)
            case None =>
              val ownerTable = coll.getOwner.getTable
              val logicalOwnerTableName = ownerTable.getName
              var tblName = mappings.getNamingStrategy.collectionTableName(
                coll.getOwner.getEntityName, logicalOwnerTableName, null, null, seqp.name)
              if (ownerTable.isQuoted) quote(tblName) else tblName
          }
          val table = mappings.addTable(seqp.schema.orNull, null, tableName, seqp.subselect.orNull, false)
          coll.setCollectionTable(table)
      }
    }

    seqp.sort match {
      case None => coll.setSorted(false)
      case Some(sort) =>
        coll.setSorted(true)
        if (sort != "natural") coll.setComparatorClassName(sort)
    }
    seqp match {
      case seqp: SeqProperty =>
        mappings.addSecondPass(new CollSecondPass(seqp, mappings, coll))
      case setp: SetProperty =>
        mappings.addSecondPass(new CollSecondPass(seqp, mappings, coll))
      case mapp: MapProperty =>
        mappings.addSecondPass(new MapSecondPass(mapp, mappings, coll.asInstanceOf[HMap]))
    }
    coll
  }

  def createProperty(value: Value, propertyName: String, clazz: Class[_], pm: PropertyConfig, mappings: Mappings): Property = {
    setTypeUsingReflection(value, clazz, propertyName)
    value match {
      case toOne: ToOne =>
        val propertyRef = toOne.getReferencedPropertyName
        if (propertyRef != null) {
          mappings.addUniquePropertyReference(toOne.getReferencedEntityName, propertyRef)
        }
      //      toOne.setCascadeDeleteEnabled( "cascade".equals(subnode.attributeValue( "on-delete" ) ) )
      case coll: Collection =>
        val propertyRef = coll.getReferencedPropertyName
        if (propertyRef != null) mappings.addPropertyReference(coll.getOwnerEntityName, propertyRef)
      case _ =>
    }

    value.createForeignKey
    val prop = new Property
    prop.setValue(value)
    bindProperty(pm, prop, mappings)
    prop
  }

  def bindProperty(pm: PropertyConfig, property: Property, mappings: Mappings): Unit = {
    property.setName(pm.name)
    property.setPropertyAccessorName(pm.access.getOrElse(mappings.getDefaultAccess))
    property.setCascade(pm.cascade.getOrElse(mappings.getDefaultCascade))
    property.setUpdateable(pm.updateable)
    property.setInsertable(pm.insertable)
    property.setOptimisticLocked(pm.optimisticLocked)
    pm.generated foreach { v =>
      val generationTiming = GenerationTiming.parseFromName(v)
      property.setValueGenerationStrategy(new GeneratedValueGeneration(generationTiming))
      if (pm.insertable) throw new MappingException(s"both insertable and generated finded for property: ${pm.name}")
      if (pm.updateable && generationTiming == GenerationTiming.ALWAYS) throw new MappingException(s"both updateable and generated finded for property: ${pm.name}")
    }
    property.setLazy(pm.lazyed)
  }
}
/**
 * Hibernate Mapping Code Config Binder.
 */
class HbmConfigBinder(val mappings: Mappings) {

  import HbmConfigBinder._

  def bindClass(em: Entity): PersistentClass = {
    val entity = new RootClass
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

    val table = mappings.addTable(em.schema, null, getClassTableName(entity, em), null, em.isAbstract)
    entity.setTable(table)
    em.properties foreach {
      case (propertyName, p) =>
        var value: Value = null
        val pt = p.propertyType
        p match {
          case idp: IdProperty =>
            bindSimpleId(em, entity, idp)
            entity.createPrimaryKey
          case m21: ManyToOneProperty =>
            value = bindManyToOne(new HManyToOne(mappings, table), m21.name, m21.targetEntity, m21.columns, m21)
          case colp: CollectionProperty =>
            val hcol = createCollection(colp, entity, mappings)
            mappings.addCollection(bindCollection(entity, em.entityName + "." + colp.name, colp, hcol))
            value = hcol
          case cp: ComponentProperty =>
            val subpath = qualify(em.entityName, propertyName);
            value = new HComponent(mappings, entity);
            bindComponent(value.asInstanceOf[HComponent], cp, subpath, false, mappings)
          case sp: ScalarProperty =>
            value = bindSimpleValue(new SimpleValue(mappings, table), propertyName, sp, sp)
        }

        if (value != null) {
          val property = createProperty(value, propertyName, entity.getMappedClass, p, mappings)
          //          if (naturalId)            property.setNaturalIdentifier(true)
          //          if (uniqueKey != null) {
          //            uniqueKey.addColumns(property.getColumnIterator)
          entity.addProperty(property)
        }
    }
    mappings.addClass(entity)
    entity
  }

  private def bindSimpleId(em: Entity, entity: RootClass, idp: IdProperty): Unit = {
    val id = new SimpleValue(mappings, entity.getTable)
    entity.setIdentifier(id)
    bindColumns(idp.columns, id, idp.name)
    setTypeUsingReflection(id, entity.getMappedClass, idp.name)
    val prop = new Property
    prop.setValue(id)
    bindProperty(idp, prop, mappings)
    entity.setIdentifierProperty(prop)
    entity.setDeclaredIdentifierProperty(prop)
    makeIdentifier(em, id)
  }

  private def getClassTableName(model: PersistentClass, em: Entity): String = {
    var logicalTableName: String = null
    var physicalTableName: String = null
    if (em.table == null) {
      logicalTableName = unqualify(model.getEntityName)
      physicalTableName = mappings.getNamingStrategy.classToTableName(model.getEntityName)
    } else {
      logicalTableName = em.table
      physicalTableName = mappings.getNamingStrategy.tableName(logicalTableName)
    }
    mappings.addTableBinding(em.schema, null, logicalTableName, physicalTableName, null)
    return physicalTableName
  }
}
