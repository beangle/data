/*
 * Beangle, Agile Java/Scala Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2013, Beangle Software.
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
package org.beangle.data.hibernate

import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable

import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.logging.Logging
import org.beangle.data.hibernate.internal.PersistentClassMerger
import org.beangle.data.jpa.mapping.NamingPolicy
import org.hibernate.DuplicateMappingException
import org.hibernate.cfg.Configuration
import org.hibernate.cfg.Mappings
import org.hibernate.cfg.SettingsFactory
import org.hibernate.mapping.Collection
import org.hibernate.mapping.IdGenerator
import org.hibernate.mapping.PersistentClass

class OverrideConfiguration(settings: SettingsFactory = new SettingsFactory()) extends Configuration(settings) with Logging {

  var dynaupdateMinColumn = 7;

  override def createMappings(): Mappings = new OverrideMappings()

  /**
   * Config table's schema by TableNamingStrategy.<br>
   *
   * @see org.beangle.orm.hibernate.RailsNamingStrategy
   */
  private def configSchema() {
    var namingPolicy: NamingPolicy = null;
    if (getNamingStrategy().isInstanceOf[RailsNamingStrategy])
      namingPolicy = getNamingStrategy().asInstanceOf[RailsNamingStrategy].policy;

    if (null == namingPolicy || !namingPolicy.isMultiSchema) return

    for (clazz <- classes.values()) {
      namingPolicy.getSchema(clazz.getEntityName) foreach { schema =>
        clazz.getTable().setSchema(schema)
      }
    }

    for (collection <- collections.values()) {
      val table = collection.getCollectionTable();
      if (null != table) {
        namingPolicy.getSchema(collection.getRole()) foreach { schema =>
          table.setSchema(schema)
        }
      }
    }
  }

  /**
   * Update persistentclass and collection's schema.<br>
   * Remove duplicated persistentClass register in classes map.
   *
   * @see #addClass(Class)
   */
  protected override def secondPassCompile() {
    super.secondPassCompile();
    configSchema();
    val hackedEntityNames = new mutable.HashSet[String]
    for (entry <- classes.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().getEntityName())) hackedEntityNames.add(entry.getKey());
    }
    for (entityName <- hackedEntityNames)
      classes.remove(entityName);
  }

  protected class OverrideMappings extends MappingsImpl {
    private val tmpColls = new mutable.HashMap[String, mutable.ListBuffer[Collection]]

    /**
     * 注册缺省的sequence生成器
     */
    this.addDefaultGenerator(newTableSequence())

    private def newTableSequence(): IdGenerator = {
      val idGen = new IdGenerator()
      idGen.setName("table_sequence")
      idGen.setIdentifierGeneratorStrategy(classOf[TableSeqGenerator].getName());
      idGen
    }
    /**
     * <ul>
     * <li>First change jpaName to entityName</li>
     * <li>Duplicate register persistent class,hack hibernate(ToOneFkSecondPass.isInPrimaryKey)</li>
     * </ul>
     */
    override def addClass(pClass: PersistentClass) {
      // trigger dynamic update
      if (!pClass.useDynamicUpdate() && pClass.getTable().getColumnSpan() >= dynaupdateMinColumn) pClass
        .setDynamicUpdate(true);
      val className = pClass.getClassName();
      var entityName = pClass.getEntityName();

      var entityNameChanged = false;
      val jpaEntityName = pClass.getJpaEntityName();
      // Set real entityname using jpaEntityname
      if (null != jpaEntityName && jpaEntityName.contains(".")) {
        entityName = jpaEntityName;
        pClass.setEntityName(entityName);
        entityNameChanged = true;
      }
      // register class
      val old = classes.get(entityName).asInstanceOf[PersistentClass];
      if (old == null) {
        classes.put(entityName, pClass);
      } else if (old.getMappedClass().isAssignableFrom(pClass.getMappedClass())) {
        PersistentClassMerger.merge(pClass, old);
      }
      // 为了欺骗hibernate中的ToOneFkSecondPass的部分代码,例如isInPrimaryKey。这些代码会根据className查找persistentClass，而不是根据entityName
      if (entityNameChanged) classes.put(className, pClass);

      // add entitis collections
      var cols = tmpColls.remove(entityName);
      if (cols.isEmpty) cols = tmpColls.remove(className);
      if (!cols.isEmpty) {
        for (col <- cols.get) {
          val colName = if (col.getRole().startsWith(className)) col.getRole().substring(className.length() + 1)
          else col.getRole().substring(entityName.length() + 1)
          col.setRole(entityName + "." + colName);
          collections.put(col.getRole(), col);
        }
      }
    }

    /**
     * Duplicated entity name in sup/subclass situation will rise a
     * <code>DuplicateMappingException</code>
     */
    override def addImport(entityName: String, rename: String) {
      val existing = imports.get(rename);
      if (null == existing) {
        imports.put(rename, entityName);
      } else {
        if (ClassLoaders.loadClass(existing).isAssignableFrom(ClassLoaders.loadClass(entityName))) {
          imports.put(rename, entityName)
        } else {
          throw new DuplicateMappingException("duplicate import: " + rename + " refers to both "
            + entityName + " and " + existing + " (try using auto-import=\"false\")", "import", rename)
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
      val entityName = collection.getOwnerEntityName();
      tmpColls.get(entityName) match {
        case Some(list) => list += collection;
        case None => {
          val newlist = new mutable.ListBuffer[Collection]
          newlist += collection
          tmpColls.put(entityName, newlist);
        }
      }
    }
  }
}