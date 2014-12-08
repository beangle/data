/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jpa.hibernate

import java.lang.reflect.Field
import java.{ util => ju }
import scala.collection.JavaConversions.{ asScalaBuffer, asScalaSet, collectionAsScalaIterable }
import scala.collection.mutable
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.hibernate.id.{ AutoIncrementGenerator, CodeStyleGenerator, DateStyleGenerator, TableSeqGenerator }
import org.beangle.data.jpa.hibernate.udt.{ MapType, OptionBooleanType, OptionByteType, OptionCharType, OptionDoubleType, OptionFloatType, OptionIntType, OptionLongType, SeqType, SetType }
import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.DuplicateMappingException
import org.hibernate.DuplicateMappingException.Type
import org.hibernate.cfg.{ Configuration, Mappings }
import org.hibernate.mapping.{ Collection, IdGenerator, MappedSuperclass, PersistentClass, Property, RootClass }
import org.beangle.data.jpa.hibernate.udt.EnumType
import org.beangle.data.jpa.hibernate.udt.HourMinuteType
import org.beangle.data.jpa.hibernate.udt.WeekStateType

class OverrideConfiguration extends Configuration with Logging {

  var minColumnEnableDynaUpdate = 7

  override def createMappings(): Mappings = new OverrideMappings()

  /**
   * Config table's schema by TableNamingStrategy.<br>
   *
   * @see org.beangle.data.jpa.hibernate.RailsNamingStrategy
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
      if (null != table) {
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

  protected class OverrideMappings extends MappingsImpl {
    private val tmpColls = new mutable.HashMap[String, mutable.ListBuffer[Collection]]

    /**
     * 注册缺省的sequence生成器
     */
    addGenerator("table_sequence", classOf[TableSeqGenerator])
    addGenerator("auto_increment", classOf[AutoIncrementGenerator])
    addGenerator("date", classOf[DateStyleGenerator])
    addGenerator("code", classOf[CodeStyleGenerator])

    addCustomTypes()

    private def addCustomTypes() {
      Map(("seq", classOf[SeqType]), ("set", classOf[SetType]),
        ("map", classOf[MapType]), ("byte?", classOf[OptionByteType]),
        ("char?", classOf[OptionCharType]), ("int?", classOf[OptionIntType]),
        ("bool?", classOf[OptionBooleanType]), ("long?", classOf[OptionLongType]),
        ("float?", classOf[OptionFloatType]), ("double?", classOf[OptionDoubleType])) foreach {
          case (name, clazz) => addTypeDef(name, clazz.getName, new ju.Properties)
        }
      val p = new ju.Properties
      p.put("enumClass", "org.beangle.commons.lang.time.WeekDays")
      addTypeDef("weekday", classOf[EnumType].getName, p)
      addTypeDef("hourminute", classOf[HourMinuteType].getName, new ju.Properties)
      addTypeDef("weekstate", classOf[WeekStateType].getName, new ju.Properties)
    }
    /**
     * Add default generator for annotation and xml parsing
     */
    private def addGenerator(name: String, clazz: Class[_]): Unit = {
      val idGen = new IdGenerator()
      idGen.setName(name)
      idGen.setIdentifierGeneratorStrategy(clazz.getName)
      addDefaultGenerator(idGen)
      getIdentifierGeneratorFactory().register(name, clazz)
    }
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
        if (ClassLoaders.loadClass(existing).isAssignableFrom(ClassLoaders.loadClass(entityName))) {
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

private[hibernate] object PersistentClassMerger extends Logging {

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
      case e: Exception => error(s"Cannot access PersistentClass $name field ,Override Mapping will be disabled", e)
    }
    null
  }

  def merge(sub: PersistentClass, parent: PersistentClass) {
    if (!mergeSupport) throw new RuntimeException("Merge not supported!")

    val className = sub.getClassName()
    // 1. convert old to mappedsuperclass
    val msc = new MappedSuperclass(parent.getSuperMappedSuperclass(), null)
    msc.setMappedClass(parent.getMappedClass())

    // 2.clear old subclass property
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
      while (pIter.hasNext()) {
        parent.addProperty(pIter.next().asInstanceOf[Property])
      }
    } catch {
      case e: Exception =>
    }
    info(s"${sub.getClassName()} replace ${parent.getClassName()}.")
  }
}
