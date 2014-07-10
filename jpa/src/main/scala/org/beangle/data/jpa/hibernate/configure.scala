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

import java.io.Serializable
import java.lang.reflect.Field

import scala.collection.JavaConversions.{ asScalaBuffer, asScalaSet, collectionAsScalaIterable }
import scala.collection.mutable

import org.beangle.commons.lang.{ ClassLoaders, Strings }
import org.beangle.commons.logging.Logging
import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.{ AssertionFailure, DuplicateMappingException }
import org.hibernate.DuplicateMappingException.Type
import org.hibernate.cfg.{ Configuration, Mappings, NamingStrategy }
import org.hibernate.mapping.{ Collection, IdGenerator, MappedSuperclass, PersistentClass, Property, RootClass }

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

    if (null == namingPolicy || !namingPolicy.hasSchema) return

    for (clazz <- classes.values()) {
      namingPolicy.getSchema(clazz.getEntityName) foreach { schema =>
        clazz.getTable().setSchema(schema)
      }
    }

    for (collection <- collections.values()) {
      val table = collection.getCollectionTable()
      if (null != table) {
        namingPolicy.getSchema(collection.getRole()) foreach (schema => table.setSchema(schema))
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
    addGenerator("date", classOf[DateStyleGenerator])
    addGenerator("code", classOf[CodeStyleGenerator])

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

/**
 * 类似Rails的数据库表名、列名命名策略
 */
object RailsNamingStrategy {

  var namingPolicy: NamingPolicy = _
}

@SerialVersionUID(-2656604564223895758L)
class RailsNamingStrategy(val policy: NamingPolicy) extends NamingStrategy with Logging with Serializable {

  RailsNamingStrategy.namingPolicy = policy

  /**
   * 根据实体名(entityName)命名表
   */
  override def classToTableName(className: String): String = {
    val tableName = policy.classToTableName(className)
    if (tableName.length > NamingPolicy.defaultMaxLength) warn(s"$tableName's length greate than 30!")
    debug(s"Mapping entity[$className] to $tableName")
    tableName
  }

  /**
   * 对自动起名和使体内集合配置的表名，添加前缀
   *
   * <pre>
   * 配置好的实体表名和关联表的名字都会经过此方法。
   * </re>
   */
  override def tableName(tableName: String): String = tableName

  /** 对配置文件起好的列名,不进行处理 */
  override def columnName(columnName: String): String = columnName

  /**
   * 数据列的逻辑名
   *
   * <pre>
   * 如果有列名，不做处理，否则按照属性自动起名.
   * 该策略保证columnName=logicalColumnName
   * </pre>
   */
  override def logicalColumnName(columnName: String, propertyName: String): String = {
    if (Strings.isNotEmpty(columnName)) columnName else propertyToColumnName(propertyName)
  }

  /**
   * 根据属性名自动起名
   *
   * <pre>
   * 将混合大小写，带有.分割的属性描述，转换成下划线分割的名称。
   * 属性名字包括：简单属性、集合属性、组合属性(component.name)
   * </pre>
   */
  override def propertyToColumnName(propertyName: String): String = {
    if (isManyToOne) addUnderscores(unqualify(propertyName)) + "_id"
    else addUnderscores(unqualify(propertyName))
  }

  /** Return the argument */
  override def joinKeyColumnName(joinedColumn: String, joinedTable: String): String = columnName(joinedColumn)

  /** Return the property name or propertyTableName */
  override def foreignKeyColumnName(propertyName: String, propertyEntityName: String,
    propertyTableName: String, referencedColumnName: String): String = {
    var header = if (null == propertyName) propertyTableName else unqualify(propertyName)
    if (header == null) { throw new AssertionFailure("NamingStrategy not properly filled") }
    header = if (isManyToOne) addUnderscores(header) else addUnderscores(propertyTableName)
    return header + "_" + referencedColumnName

  }

  /** Collection Table */
  override def collectionTableName(ownerEntity: String, ownerEntityTable: String, associatedEntity: String,
    associatedEntityTable: String, propertyName: String): String = {
    var ownerTable: String = null
    // Just for annotation configuration,it's ownerEntity is classname(not entityName), and
    // ownerEntityTable is class shortname
    if (Character.isUpperCase(ownerEntityTable.charAt(0))) {
      ownerTable = policy.classToTableName(ownerEntity)
    } else {
      ownerTable = tableName(ownerEntityTable)
    }
    val tblName = policy.collectionToTableName(ownerEntity, ownerTable, propertyName)
    if (tblName.length() > NamingPolicy.defaultMaxLength) warn(s"$tblName's length greate than 30!")
    tblName
  }

  /**
   * Returns either the table name if explicit or if there is an associated
   * table, the concatenation of owner entity table and associated table
   * otherwise the concatenation of owner entity table and the unqualified
   * property name
   */
  override def logicalCollectionTableName(tableName: String, ownerEntityTable: String,
    associatedEntityTable: String, propertyName: String): String = {
    if (tableName == null) {
      // use of a stringbuilder to workaround a JDK bug
      new StringBuilder(ownerEntityTable).append("_")
        .append(if (associatedEntityTable == null) unqualify(propertyName) else associatedEntityTable).toString()
    } else {
      tableName
    }
  }

  /**
   * Return the column name if explicit or the concatenation of the property
   * name and the referenced column
   */
  override def logicalCollectionColumnName(columnName: String, propertyName: String, referencedColumn: String): String = {
    if (Strings.isNotEmpty(columnName)) columnName else (unqualify(propertyName) + "_" + referencedColumn)
  }

  final def addUnderscores(name: String): String = Strings.unCamel(name.replace('.', '_'), '_')

  final def unqualify(qualifiedName: String): String = {
    val loc = qualifiedName.lastIndexOf('.')
    if (loc < 0) qualifiedName else qualifiedName.substring(loc + 1)
  }
  /**
   * 检查是否为ManyToOne调用
   */
  private def isManyToOne: Boolean = {
    val trace = Thread.currentThread().getStackTrace()
    var matched = false
    if (trace.length >= 9) {
      matched = (2 to 8) exists { i =>
        ("bindManyToOne" == trace(i).getMethodName() || trace(i).getClassName().equals("org.hibernate.cfg.ToOneFkSecondPass"))
      }
    }
    matched
  }
}
