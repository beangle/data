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
package org.beangle.data.hibernate.tool

import java.io.{ FileWriter, Writer }
import java.{ util => ju }
import java.util.Locale

import org.beangle.commons.lang.{ ClassLoaders, Locales, Strings }
import org.beangle.commons.lang.Strings.{ isBlank, split, substringAfter, substringAfterLast, substringBeforeLast }
import org.beangle.commons.lang.SystemInfo
import org.beangle.commons.logging.Logging
import org.beangle.commons.i18n.Messages
import org.beangle.data.hibernate.cfg.{ ConfigurationBuilder, OverrideConfiguration }
import org.hibernate.cfg.AvailableSettings.{ DEFAULT_CATALOG, DEFAULT_SCHEMA, DIALECT }
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.Dialect
import org.hibernate.engine.spi.Mapping
import org.hibernate.id.PersistentIdentifierGenerator
import org.hibernate.mapping.{ Collection, Column, Component, ForeignKey, IdentifierCollection, IndexedCollection, KeyValue, ManyToOne, PersistentClass, Property, RootClass, SimpleValue, Table, ToOne }

/**
 * Generate DDL and Sequences and Comments
 */
object DdlGenerator {
  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      System.out.println("Usage: DdlGenerator org.hibernate.dialect.PostgreSQL9Dialect /tmp zh_CN com.my.package")
      return
    }
    var dir = SystemInfo.tmpDir
    if (args.length > 1) dir = args(1)
    var locale = Locale.getDefault
    if (args.length > 2) locale = Locales.toLocale(args(2))
    var pattern: String = null
    if (args.length > 3) pattern = args(3)

    var dialect = args(0)

    if (!dialect.contains(".")) {
      if (!dialect.endsWith("Dialect")) dialect += "Dialect"
      dialect = "org.hibernate.dialect." + Strings.capitalize(dialect)
    }
    new DdlGenerator(ClassLoaders.load(dialect).newInstance.asInstanceOf[Dialect], locale).gen(dir, pattern)
  }
}

class DdlGenerator(dialect: Dialect, locale: Locale) extends Logging {
  private var configuration: Configuration = _
  private val schemas = new collection.mutable.ListBuffer[String]
  private val tables = new collection.mutable.ListBuffer[String]
  private val sequences = new collection.mutable.ListBuffer[String]
  private val comments = new collection.mutable.ListBuffer[String]
  private val constraints = new collection.mutable.ListBuffer[String]
  private val indexes = new collection.mutable.ListBuffer[String]
  private var messages = Messages(locale)

  private var defaultCatalog: String = _
  private var defaultSchema: String = _

  private var mapping: Mapping = _
  private val processed = new collection.mutable.HashSet[Table]

  private val files = List(
    "0-schemas.sql" -> List(schemas),
    "1-tables.sql" -> List(tables, constraints, indexes),
    "2-sequences.sql" -> List(sequences),
    "3-comments.sql" -> List(comments))

  /**
   * Generate sql scripts
   */
  def gen(dirName: String, packageName: String): Unit = {
    configuration = ConfigurationBuilder.build(new OverrideConfiguration)
    mapping = configuration.buildMapping
    defaultCatalog = configuration.getProperties.getProperty(DEFAULT_CATALOG)
    defaultSchema = configuration.getProperties.getProperty(DEFAULT_SCHEMA)
    configuration.getProperties.put(DIALECT, dialect)

    // 1. first process class mapping
    val schemaSet = new collection.mutable.HashSet[String]
    val iterpc = configuration.getClassMappings
    while (iterpc.hasNext) {
      val pc = iterpc.next
      val clazz = pc.getMappedClass
      val table = pc.getTable
      if (!isBlank(table.getSchema)) schemaSet += table.getSchema
      table.setComment(getComment(clazz, clazz.getSimpleName))
      commentIdProperty(clazz, table, pc.getIdentifierProperty, pc.getIdentifier)
      commentProperties(clazz, table, pc.getPropertyIterator)

      if (isBlank(packageName) || clazz.getPackage.getName.startsWith(packageName)) {
        if (pc.isInstanceOf[RootClass]) {
          val ig = pc.getIdentifier.createIdentifierGenerator(
            configuration.getIdentifierGeneratorFactory, dialect, defaultCatalog, defaultSchema, pc.asInstanceOf[RootClass])
          if (ig.isInstanceOf[PersistentIdentifierGenerator]) {
            sequences ++= ig.asInstanceOf[PersistentIdentifierGenerator].sqlCreateStrings(dialect)
          }
        }
        generateTableSql(table)
      }
    }

    // 2. process collection mapping
    val itercm = configuration.getCollectionMappings
    while (itercm.hasNext) {
      val col = itercm.next.asInstanceOf[Collection]
      if (isBlank(packageName) || col.getRole.startsWith(packageName)) {
        // collection sequences
        if (col.isIdentified) {
          val ig = col.asInstanceOf[IdentifierCollection].getIdentifier.createIdentifierGenerator(
            configuration.getIdentifierGeneratorFactory, dialect, defaultCatalog, defaultSchema, null)

          if (ig.isInstanceOf[PersistentIdentifierGenerator]) {
            sequences ++= ig.asInstanceOf[PersistentIdentifierGenerator].sqlCreateStrings(dialect)
          }
        }
        // collection table
        if (!col.isOneToMany) {
          val table = col.getCollectionTable
          val owner = col.getTable.getComment
          if (!isBlank(table.getSchema)) schemaSet += table.getSchema
          var ownerClass = col.getOwner.getMappedClass
          // resolved nested compoent name in collection's role
          val colName = substringAfter(col.getRole, col.getOwnerEntityName + ".")
          if (colName.contains(".")) ownerClass = getPropertyType(col.getOwner, substringBeforeLast(colName, "."))
          table.setComment(owner + "-" + getComment(ownerClass, substringAfterLast(col.getRole, ".")))

          val keyColumn = table.getColumn(col.getKey.getColumnIterator.next.asInstanceOf[Column])
          if (null != keyColumn) keyColumn.setComment(owner + "ID")

          if (col.isInstanceOf[IndexedCollection]) {
            val idxCol = col.asInstanceOf[IndexedCollection]
            val idx = idxCol.getIndex
            if (idx.isInstanceOf[ToOne]) commentToOne(idx.asInstanceOf[ToOne], idx.getColumnIterator.next.asInstanceOf[Column])
          }

          col.getElement match {
            case mto: ManyToOne =>
              val valueColumn = col.getElement.getColumnIterator.next.asInstanceOf[Column]
              commentToOne(mto, valueColumn)
            case cp: Component =>
              commentProperties(cp.getComponentClass, table, cp.getPropertyIterator)
            case _ =>
          }
          generateTableSql(col.getCollectionTable)
        }
      }
    }
    val newcomments = comments.toSet.toList
    comments.clear
    comments ++= newcomments
    schemas ++= schemaSet.map(s => s"create schema $s")
    schemas.sorted

    // 3. export to files
    var total = 0
    files foreach {
      case (key, sqls) =>
        val sqlCount = sqls.foldLeft(0)((sum, l) => sum + l.size)
        total += sqlCount
        if (sqlCount > 0) {
          println(s"writing $sqlCount sqls to " + dirName + "/" + key)
          val writer = new FileWriter(dirName + "/" + key, false)
          writes(writer, sqls)
          writer.flush
          writer.close
        }
    }
    if (total == 0)
      println("Cannot find hibernate mapping files or classes,DDL generation aborted.")
  }

  /**
   * get component class by component property string
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
    val entityName = toOne.getReferencedEntityName
    val referClass = configuration.getClassMapping(entityName)
    if (null != referClass) {
      column.setComment(referClass.getTable.getComment + "ID")
    }
  }

  private def commentIdProperty(clazz: Class[_], table: Table, p: Property, identifier: KeyValue): Unit = {
    if (p.getColumnSpan == 1) {
      val column = p.getColumnIterator.next.asInstanceOf[Column]
      var comment = getComment(clazz, p.getName)
      identifier match {
        case sv: SimpleValue => comment += (":" + sv.getIdentifierGeneratorStrategy + toString(sv.getIdentifierGeneratorProperties))
        case _               =>
      }
      column.setComment(comment)
    } else if (p.getColumnSpan > 1) {
      val pc = p.getValue.asInstanceOf[Component]
      val columnOwnerClass = pc.getComponentClass
      commentProperties(columnOwnerClass, table, pc.getPropertyIterator)
    }
  }

  private def toString(properties: ju.Properties): String = {
    if (properties.isEmpty) return ""
    val result = new collection.mutable.HashMap[String, String]
    val iter = properties.propertyNames
    while (iter.hasMoreElements) {
      val p = iter.nextElement.asInstanceOf[String]
      val value = properties.getProperty(p)
      if (null != value) result.put(p, value)
    }
    if (result.isEmpty) "" else result.toString.replace("Map", "")
  }
  private def commentProperty(clazz: Class[_], table: Table, p: Property): Unit = {
    if (null == p) return
    if (p.getColumnSpan == 1) {
      val column = p.getColumnIterator.next.asInstanceOf[Column]
      if (isForeignColumn(table, column)) {
        column.setComment(getComment(clazz, p.getName) + "ID")
      } else {
        column.setComment(getComment(clazz, p.getName))
      }
    } else if (p.getColumnSpan > 1) {
      val pc = p.getValue.asInstanceOf[Component]
      val columnOwnerClass = pc.getComponentClass
      commentProperties(columnOwnerClass, table, pc.getPropertyIterator)
    }
  }

  private def commentProperties(clazz: Class[_], table: Table, ip: ju.Iterator[_]) {
    while (ip.hasNext)
      commentProperty(clazz, table, ip.next.asInstanceOf[Property])
  }

  private def generateTableSql(table: Table): Unit = {
    if (!table.isPhysicalTable) return
    val commentIter = table.sqlCommentStrings(dialect, defaultCatalog, defaultSchema)
    while (commentIter.hasNext) comments += commentIter.next.toString

    if (processed.contains(table)) return
    processed.add(table)
    tables += table.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)

    val subIter = table.getUniqueKeyIterator
    while (subIter.hasNext) {
      val uk = subIter.next
      val constraintString = uk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
      if (constraintString != null) constraints += constraintString
    }

    val idxIter = table.getIndexIterator
    while (idxIter.hasNext) {
      val index = idxIter.next
      indexes += index.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
    }

    if (dialect.hasAlterTable) {
      val fkIter = table.getForeignKeyIterator
      while (fkIter.hasNext) {
        val fk = fkIter.next.asInstanceOf[ForeignKey]
        if (fk.isPhysicalConstraint) {
          constraints += fk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
        }
      }
    }
  }

  private def isForeignColumn(table: Table, column: Column): Boolean = {
    val fkIter = table.getForeignKeyIterator
    while (fkIter.hasNext) {
      val fk = fkIter.next.asInstanceOf[ForeignKey]
      if (fk.isPhysicalConstraint) {
        if (fk.getColumns.contains(column)) return true
      }
    }
    return false
  }

  private def getComment(clazz: Class[_], key: String): String = {
    val comment = messages.get(clazz, key)
    if (key == comment) {
      logger.warn(s"Cannot find comment of ${clazz.getName}.$key")
      key + "?"
    } else {
      comment
    }
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