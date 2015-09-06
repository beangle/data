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
package org.beangle.data.jpa.hibernate.cfg

import java.lang.reflect.Field
import java.{ util => ju }
import scala.collection.JavaConversions.{ asScalaBuffer, asScalaSet, collectionAsScalaIterable }
import scala.collection.mutable
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.hibernate.PropertyAccessor
import org.beangle.data.jpa.hibernate.id.{ AutoIncrementGenerator, CodeStyleGenerator, DateStyleGenerator, TableSeqGenerator }
import org.beangle.data.jpa.hibernate.udt.{ EnumType, MapType, OptionBooleanType, OptionByteType, OptionCharType, OptionDoubleType, OptionFloatType, OptionIntType, OptionLongType, SeqType, SetType, ValueType }
import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.DuplicateMappingException
import org.hibernate.DuplicateMappingException.Type
import org.hibernate.cfg.{ Configuration, Mappings }
import org.hibernate.mapping.{ Collection, IdGenerator, MappedSuperclass, PersistentClass, Property, RootClass, SimpleValue }
import org.beangle.commons.lang.time.HourMinute
import org.beangle.commons.lang.time.WeekDay._
/**
 * Override Configuration
 */
class OverrideConfiguration extends Configuration with Logging {

  var minColumnEnableDynaUpdate = 7

  override def createMappings(): Mappings = {
    val mappings = new OverrideMappings(getProperty("hibernate.global_id_generator"))
    // 注册缺省的sequence生成器
    addGenerator(mappings, "table_sequence", classOf[TableSeqGenerator])
    addGenerator(mappings, "auto_increment", classOf[AutoIncrementGenerator])
    addGenerator(mappings, "date", classOf[DateStyleGenerator])
    addGenerator(mappings, "code", classOf[CodeStyleGenerator])
    // 注册CustomTypes
    addCustomTypes(mappings)
    mappings.setDefaultAccess(classOf[PropertyAccessor].getName)
    mappings
  }

  private def addCustomTypes(mappings: MappingsImpl) {
    Map(("seq", classOf[SeqType]), ("set", classOf[SetType]),
      ("map", classOf[MapType]), ("byte?", classOf[OptionByteType]),
      ("char?", classOf[OptionCharType]), ("int?", classOf[OptionIntType]),
      ("bool?", classOf[OptionBooleanType]), ("long?", classOf[OptionLongType]),
      ("float?", classOf[OptionFloatType]), ("double?", classOf[OptionDoubleType])) foreach {
        case (name, clazz) => mappings.addTypeDef(name, clazz.getName, new ju.Properties)
      }
  }

  /**
   * Add default generator for annotation and xml parsing
   */
  private def addGenerator(mappings: MappingsImpl, name: String, clazz: Class[_]): Unit = {
    val idGen = new IdGenerator()
    idGen.setName(name)
    idGen.setIdentifierGeneratorStrategy(clazz.getName)
    mappings.addDefaultGenerator(idGen)
    mappings.getIdentifierGeneratorFactory().register(name, clazz)
  }

  /**
   * Config table's schema by TableNamingStrategy.<br>
   *
   * @see org.beangle.data.jpa.hibernate.cfg.RailsNamingStrategy
   */
  private def configSchema() {
    var namingPolicy: NamingPolicy = null
    if (getNamingStrategy().isInstanceOf[RailsNamingStrategy])
      namingPolicy = getNamingStrategy().asInstanceOf[RailsNamingStrategy].policy

    if (null == namingPolicy || !namingPolicy.multiSchema) return

    for (clazz <- classes.values()) {
      namingPolicy.getSchema(clazz.getMappedClass) foreach { schema =>
        clazz.getTable().setSchema(schema)
      }
    }

    for (collection <- collections.values()) {
      val table = collection.getCollectionTable()
      if (null != table && !collection.isOneToMany) {
        namingPolicy.getSchema(collection.getOwner.getMappedClass) foreach (schema => table.setSchema(schema))
      }
    }
  }

  /**
   * Update persistentclass and collection's schema.<br>
   * Remove duplicated persistentClass register in classes map.
   */
  protected override def secondPassCompile() {
    super.secondPassCompile()
    configSchema()
    val hackedEntityNames = new mutable.HashSet[String]
    for (entry <- classes.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().getEntityName())) hackedEntityNames.add(entry.getKey())
    }
    for (entityName <- hackedEntityNames)
      classes.remove(entityName)
  }

  /**
   * Custom MappingsImpl supports class overriding
   */
  protected class OverrideMappings(val globalIdGenerator: String) extends MappingsImpl {
    private val tmpColls = new mutable.HashMap[String, mutable.ListBuffer[Collection]]
    /**
     * <ul>
     * <li>First change jpaName to entityName</li>
     * <li>Duplicate register persistent class,hack hibernate(ToOneFkSecondPass.isInPrimaryKey)</li>
     * </ul>
     */
    override def addClass(pClass: PersistentClass) {
      // trigger dynamic update
      if (!pClass.useDynamicUpdate() && pClass.getTable().getColumnSpan() >= minColumnEnableDynaUpdate) pClass
        .setDynamicUpdate(true)
      val className = pClass.getClassName()
      var entityName = pClass.getEntityName()

      var entityNameChanged = false
      val jpaEntityName = pClass.getJpaEntityName()
      // Set real entityname using jpaEntityname
      if (null != jpaEntityName && jpaEntityName.contains(".")) {
        entityName = jpaEntityName
        pClass.setEntityName(entityName)
        entityNameChanged = true
      }

      if (null != globalIdGenerator) {
        pClass match {
          case rc: RootClass =>
            rc.getIdentifier() match {
              case sv: SimpleValue =>
                sv.setIdentifierGeneratorStrategy(globalIdGenerator)
                sv.setIdentifierGeneratorProperties(new ju.Properties)
              case _ =>
            }
          case _ =>
        }
      }
      // register class
      val old = classes.get(entityName).asInstanceOf[PersistentClass]
      if (old == null) {
        classes.put(entityName, pClass)
      } else if (old.getMappedClass().isAssignableFrom(pClass.getMappedClass())) {
        PersistentClassMerger.merge(pClass, old)
      }
      // 为了欺骗hibernate中的ToOneFkSecondPass的部分代码,例如isInPrimaryKey。这些代码会根据className查找persistentClass，而不是根据entityName
      if (entityNameChanged) classes.put(className, pClass)

      // add entitis collections
      var cols = tmpColls.remove(entityName)
      if (cols.isEmpty) cols = tmpColls.remove(className)
      if (!cols.isEmpty) {
        for (col <- cols.get) {
          val colName = if (col.getRole().startsWith(className)) col.getRole().substring(className.length() + 1)
          else col.getRole().substring(entityName.length() + 1)
          col.setRole(entityName + "." + colName)
          collections.put(col.getRole(), col)
        }
      }
    }

    /**
     * Duplicated entity name in sup/subclass situation will rise a
     * <code>DuplicateMappingException</code>
     */
    override def addImport(entityName: String, rename: String) {
      val existing = imports.get(rename)
      if (null == existing) {
        imports.put(rename, entityName)
      } else {
        if (ClassLoaders.load(existing).isAssignableFrom(ClassLoaders.load(entityName))) {
          imports.put(rename, entityName)
        } else {
          throw new DuplicateMappingException("duplicate import: " + rename + " refers to both "
            + entityName + " and " + existing + " (try using auto-import=\"false\")", Type.ENTITY, rename)
        }
      }
    }

    /**
     * Delay register collection,let class descide which owner will be winner.
     * <ul>
     * <li>Provide override collections with same rolename.
     * <li>Delay register collection,register by addClass method
     * </ul>
     */
    override def addCollection(collection: Collection) {
      val entityName = collection.getOwnerEntityName()
      tmpColls.get(entityName) match {
        case Some(list) => list += collection
        case None => {
          val newlist = new mutable.ListBuffer[Collection]
          newlist += collection
          tmpColls.put(entityName, newlist)
        }
      }
    }
  }
}

private[cfg] object PersistentClassMerger extends Logging {

  private val subPropertyField = getField("subclassProperties")
  private val declarePropertyField = getField("declaredProperties")
  private val subclassField = getField("subclasses")

  val mergeSupport = (null != subPropertyField) && (null != declarePropertyField) && (null != subclassField)

  private def getField(name: String): Field = {
    try {
      val field = classOf[PersistentClass].getDeclaredField(name)
      field.setAccessible(true)
      field
    } catch {
      case e: Exception => {
        logger.error(s"Cannot access PersistentClass $name field ,Override Mapping will be disabled", e)
        null
      }
    }
  }

  /**
   * Merge sub property into parent
   *
   * Make parent persistent class as TARGET.
   *
   * 1. Modify parent className using sub's className
   * 2. Clear any sub/super/polymorphic/discriminator value
   * 3. Put all properties into parent
   */
  def merge(sub: PersistentClass, parent: PersistentClass): Unit = {
    if (!mergeSupport) throw new RuntimeException("Merge not supported!")
    if (sub.getMappedClass == parent.getMappedClass) return ;

    val className = sub.getClassName()
    // 1. convert old to mappedsuperclass
    val msc = new MappedSuperclass(parent.getSuperMappedSuperclass(), null)
    msc.setMappedClass(parent.getMappedClass())

    // 2.clear old subclass property
    val parentClassName=parent.getClassName
    parent.setSuperMappedSuperclass(msc)
    parent.setClassName(className)
    parent.setProxyInterfaceName(className)
    if (parent.isInstanceOf[RootClass]) {
      val rootParent = parent.asInstanceOf[RootClass]
      rootParent.setDiscriminator(null)
      rootParent.setPolymorphic(false)
    }
    try {
      val declareProperties = declarePropertyField.get(parent).asInstanceOf[java.util.List[Property]]
      for (p <- declareProperties)
        msc.addDeclaredProperty(p)
      subPropertyField.get(parent).asInstanceOf[java.util.List[_]].clear()
      subclassField.get(parent).asInstanceOf[java.util.List[_]].clear()
    } catch {
      case e: Exception =>
    }

    // 3. add property to old
    try {
      val pIter = sub.getPropertyIterator()
      val ppIter = parent.getPropertyIterator;
      val pps = Collections.newSet[String]
      while (ppIter.hasNext()) {
        pps += ppIter.next().asInstanceOf[Property].getName
      }
      while (pIter.hasNext()) {
        val property = pIter.next().asInstanceOf[Property]
        if (!pps.contains(property.getName)) parent.addProperty(property)
      }
    } catch {
      case e: Exception =>
    }
    logger.info(s"${sub.getClassName()} replace $parentClassName.")
  }
}
