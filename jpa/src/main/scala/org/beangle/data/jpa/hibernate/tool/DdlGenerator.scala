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
package org.beangle.data.jpa.hibernate.tool

import java.io.{ FileWriter, Writer }
import java.{ util => ju }
import java.util.Locale

import org.beangle.commons.lang.{ ClassLoaders, Locales }
import org.beangle.commons.lang.Strings.{ isBlank, split, substringAfter, substringAfterLast, substringBeforeLast }
import org.beangle.commons.lang.SystemInfo
import org.beangle.data.jpa.hibernate.{ DefaultConfigurationBuilder, OverrideConfiguration }
import org.beangle.data.model.comment.Messages
import org.hibernate.cfg.AvailableSettings.{ DEFAULT_CATALOG, DEFAULT_SCHEMA, DIALECT }
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.Mapping
import org.hibernate.id.PersistentIdentifierGenerator
import org.hibernate.mapping.{ Collection, Column, Component, ForeignKey, IdentifierCollection, IndexedCollection, ManyToOne, PersistentClass, Property, RootClass, Table, ToOne }

object DdlGenerator {
  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      System.out.println("Usage: DdlGenerator org.hibernate.dialect.Oracle10gDialect /tmp zh_CN")
      return
    }
    var dir = SystemInfo.tmpDir
    if (args.length > 1) dir = args(1)
    var locale = Locale.getDefault()
    if (args.length > 2) locale = Locales.toLocale(args(2))
    var pattern: String = null
    if (args.length > 3) pattern = args(3)
    new DdlGenerator(ClassLoaders.loadClass(args(0)).newInstance().asInstanceOf[Dialect], locale).gen(dir, pattern)
  }
}

class DdlGenerator(dialect: Dialect, locale: Locale) {
  private var configuration: Configuration = _
  private val tables = new collection.mutable.ListBuffer[String]
  private val sequences = new collection.mutable.ListBuffer[String]
  private val comments = new collection.mutable.ListBuffer[String]
  private val constraints = new collection.mutable.ListBuffer[String]
  private val indexes = new collection.mutable.ListBuffer[String]
  private var messages = Messages.build(locale)

  private var defaultCatalog: String = _
  private var defaultSchema: String = _

  private var mapping: Mapping = _
  private val processed = new collection.mutable.HashSet[Table]

  private val files = new collection.mutable.HashMap[String, List[collection.mutable.ListBuffer[String]]]

  files.put("1-tables.sql", List(tables, constraints, indexes))
  files.put("2-sequences.sql", List(sequences))
  files.put("3-comments.sql", List(comments))

  /**
   * Generate sql scripts
   *
   * @param fileName
   * @param packageName
   * @throws Exception
   */
  def gen(fileName: String, packageName: String): Unit = {
    configuration = DefaultConfigurationBuilder.build(new OverrideConfiguration())
    mapping = configuration.buildMapping()
    defaultCatalog = configuration.getProperties().getProperty(DEFAULT_CATALOG)
    defaultSchema = configuration.getProperties().getProperty(DEFAULT_SCHEMA)
    configuration.getProperties().put(DIALECT, dialect)
    // 1. first process class mapping
    val iterpc = configuration.getClassMappings()
    while (iterpc.hasNext()) {
      val pc = iterpc.next() //PersistentClass
      val clazz = pc.getMappedClass()
      if (isBlank(packageName) || clazz.getPackage().getName().startsWith(packageName)) {
        // add comment to table and column
        pc.getTable().setComment(messages.get(clazz, clazz.getSimpleName()))
        commentProperty(clazz, pc.getTable(), pc.getIdentifierProperty())
        commentProperties(clazz, pc.getTable(), pc.getPropertyIterator)
        // generator sequence sql
        if (pc.isInstanceOf[RootClass]) {
          val ig = pc.getIdentifier().createIdentifierGenerator(
            configuration.getIdentifierGeneratorFactory(), dialect, defaultCatalog, defaultSchema, pc.asInstanceOf[RootClass])
          if (ig.isInstanceOf[PersistentIdentifierGenerator]) {
            val lines = ig.asInstanceOf[PersistentIdentifierGenerator].sqlCreateStrings(dialect)
            sequences ++= lines
          }
        }
        // generater table sql
        generateTableSql(pc.getTable())
      }
    }

    // 2. process collection mapping
    val itercm = configuration.getCollectionMappings()
    while (itercm.hasNext()) {
      val col = itercm.next().asInstanceOf[Collection]
      if (isBlank(packageName) || col.getRole.startsWith(packageName)) {
        // collection sequences
        if (col.isIdentified) {
          val ig = col.asInstanceOf[IdentifierCollection].getIdentifier().createIdentifierGenerator(
            configuration.getIdentifierGeneratorFactory(), dialect, defaultCatalog, defaultSchema, null)

          if (ig.isInstanceOf[PersistentIdentifierGenerator]) {
            sequences ++= ig.asInstanceOf[PersistentIdentifierGenerator].sqlCreateStrings(dialect)
          }
        }
        // collection table
        if (!col.isOneToMany()) {
          val table = col.getCollectionTable()
          val owner = col.getTable().getComment()
          var ownerClass = col.getOwner().getMappedClass()
          // resolved nested compoent name in collection's role
          val colName = substringAfter(col.getRole(), col.getOwnerEntityName() + ".")
          if (colName.contains(".")) ownerClass = getPropertyType(col.getOwner(), substringBeforeLast(colName, "."))
          table.setComment(owner + "-" + messages.get(ownerClass, substringAfterLast(col.getRole(), ".")))

          val keyColumn = table.getColumn(col.getKey().getColumnIterator().next().asInstanceOf[Column])
          if (null != keyColumn) keyColumn.setComment(owner + " ID")

          if (col.isInstanceOf[IndexedCollection]) {
            val idxCol = col.asInstanceOf[IndexedCollection]
            val idx = idxCol.getIndex()
            if (idx.isInstanceOf[ToOne]) commentToOne(idx.asInstanceOf[ToOne], idx.getColumnIterator().next().asInstanceOf[Column])
          }

          col.getElement() match {
            case mto: ManyToOne =>
              val valueColumn = col.getElement().getColumnIterator().next().asInstanceOf[Column]
              commentToOne(mto, valueColumn)
            case cp: Component =>
              commentProperties(cp.getComponentClass(), table, cp.getPropertyIterator)
            case _ =>
          }
          generateTableSql(col.getCollectionTable())
        }
      }
    }
    val newcomments = comments.toSet.toList
    comments.clear()
    comments ++= newcomments

    // 3. export to files
    files foreach {
      case (key, sqls) =>
        println("writing " + fileName + "/" + key)
        val writer = new FileWriter(fileName + "/" + key, false)
        writes(writer, sqls)
        writer.flush()
        writer.close()
    }
  }

  /**
   * get component class by component property string
   *
   * @param pc
   * @param propertyString
   * @return
   */
  private def getPropertyType(pc: PersistentClass, propertyString: String): Class[_] = {
    val properties = split(propertyString, '.')
    var p = pc.getProperty(properties(0))
    var cp = p.getValue.asInstanceOf[Component]
    var i = 1
    while (i < properties.length) {
      p = cp.getProperty(properties(i))
      cp = p.getValue.asInstanceOf[Component]
      i += 1
    }
    cp.getComponentClass
  }

  private def commentToOne(toOne: ToOne, column: Column): Unit = {
    val entityName = toOne.getReferencedEntityName()
    val referClass = configuration.getClassMapping(entityName)
    if (null != referClass) {
      column.setComment(referClass.getTable().getComment() + " ID")
    }
  }

  private def commentProperty(clazz: Class[_], table: Table, p: Property): Unit = {
    if (null == p) return
    if (p.getColumnSpan() == 1) {
      val column = p.getColumnIterator().next().asInstanceOf[Column]
      if (isForeignColumn(table, column)) {
        column.setComment(messages.get(clazz, p.getName()) + " ID")
      } else {
        column.setComment(messages.get(clazz, p.getName()))
      }
    } else if (p.getColumnSpan() > 1) {
      val pc = p.getValue.asInstanceOf[Component]
      val columnOwnerClass = pc.getComponentClass()
      commentProperties(columnOwnerClass, table, pc.getPropertyIterator)
    }
  }

  private def commentProperties(clazz: Class[_], table: Table, ip: ju.Iterator[_]) {
    while (ip.hasNext())
      commentProperty(clazz, table, ip.next().asInstanceOf[Property])
  }

  private def generateTableSql(table: Table): Unit = {
    if (!table.isPhysicalTable()) return
    val commentIter = table.sqlCommentStrings(dialect, defaultCatalog, defaultSchema)
    while (commentIter.hasNext()) comments += commentIter.next.toString

    if (processed.contains(table)) return
    processed.add(table)
    tables += table.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)

    val subIter = table.getUniqueKeyIterator()
    while (subIter.hasNext()) {
      val uk = subIter.next()
      val constraintString = uk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
      if (constraintString != null) constraints += constraintString
    }

    val idxIter = table.getIndexIterator()
    while (idxIter.hasNext()) {
      val index = idxIter.next()
      indexes += index.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
    }

    if (dialect.hasAlterTable()) {
      val fkIter = table.getForeignKeyIterator()
      while (fkIter.hasNext()) {
        val fk = fkIter.next().asInstanceOf[ForeignKey]
        if (fk.isPhysicalConstraint) {
          constraints += fk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
        }
      }
    }
  }

  private def isForeignColumn(table: Table, column: Column): Boolean = {
    val fkIter = table.getForeignKeyIterator()
    while (fkIter.hasNext()) {
      val fk = fkIter.next().asInstanceOf[ForeignKey]
      if (fk.isPhysicalConstraint) {
        if (fk.getColumns().contains(column)) return true
      }
    }
    return false
  }

  private def writes(writer: Writer, contentList: List[collection.mutable.ListBuffer[String]]): Unit = {
    for (contents <- contentList) {
      for (script <- contents.sorted) {
        writer.write(script)
        writer.write(";\n")
      }
    }
  }
}
