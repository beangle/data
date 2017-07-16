package org.beangle.data.orm

import org.beangle.data.jdbc.meta.DBScripts
import org.beangle.data.jdbc.meta.Column
import org.beangle.data.jdbc.meta.ForeignKey
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.model.Component
import org.beangle.data.model.meta.Property
import java.io.FileWriter
import javax.persistence.ManyToOne
import java.io.Writer
import org.beangle.commons.logging.Logging
import javax.xml.crypto.dsig.keyinfo.KeyValue
import java.util.Locale
import org.beangle.commons.text.i18n.Messages
import org.beangle.data.jdbc.meta.Identifier
import org.beangle.commons.lang.Strings._
import org.beangle.data.jdbc.dialect.SQL
import org.beangle.data.jdbc.dialect.Dialect

class SchemaExporter(val mappings: Mappings, dialect: Dialect, locale: Locale, pattern: String) extends Logging {

  private var commentBundles = Messages(locale)

  private val schemas = new collection.mutable.ListBuffer[String]
  private val tables = new collection.mutable.ListBuffer[String]
  private val sequences = new collection.mutable.ListBuffer[String]
  private val comments = new collection.mutable.ListBuffer[String]
  private val constraints = new collection.mutable.ListBuffer[String]
  private val indexes = new collection.mutable.ListBuffer[String]
  private val processed = new collection.mutable.HashSet[Table]

  private def getComment(clazz: Class[_], key: String): String = {
    val comment = commentBundles.get(clazz, key)
    if (key == comment) {
      logger.warn(s"Cannot find comment of ${clazz.getName}.$key")
      key + "?"
    } else {
      comment
    }
  }
  def generate(): DBScripts = {
    // 1. first process class mapping
    val schemaSet = new collection.mutable.HashSet[String]
    mappings.entityMappings.values foreach { em =>
      val table = em.table
      val clazz = em.clazz
      table.comment = Some(getComment(clazz, clazz.getSimpleName))
      if (table.schema.name != Identifier.empty) {
        schemaSet += table.schema.name.value
      }
      if (isBlank(pattern) || clazz.getPackage.getName.startsWith(pattern)) {
        generateTableSql(table)
      }
    }
    val scripts = new DBScripts()
    schemas ++= schemaSet.map(s => s"create schema $s")
    
    scripts.schemas = schemas.sorted.toList
    scripts.comments =comments.toSet.toList.sorted
    scripts.tables =tables.sorted.toList
    scripts
  }

  private def generateTableSql(table: Table): Unit = {
    if (processed.contains(table)) return
    processed.add(table)
    comments ++= SQL.commentsOnTable(table, dialect)
    tables += SQL.createTable(table, dialect)

    //    val subIter = table.getUniqueKeyIterator
    //    while (subIter.hasNext) {
    //      val uk = subIter.next
    //      val constraintString = uk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
    //      if (constraintString != null) constraints += constraintString
    //    }
    //
    //    val idxIter = table.getIndexIterator
    //    while (idxIter.hasNext) {
    //      val index = idxIter.next
    //      indexes += index.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
    //    }
    //
    //    if (dialect.hasAlterTable) {
    //      val fkIter = table.getForeignKeyIterator
    //      while (fkIter.hasNext) {
    //        val fk = fkIter.next.asInstanceOf[ForeignKey]
    //        if (fk.isPhysicalConstraint) {
    //          constraints += fk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
    //        }
    //      }
    //    }
  }

  //  /**
  //   * Generate sql scripts
  //   */
  //  def gen(dirName: String, packageName: String): Unit = {
  //    configuration = ConfigurationBuilder.build(new OverrideConfiguration)
  //    mapping = configuration.buildMapping
  //    defaultCatalog = configuration.getProperties.getProperty(DEFAULT_CATALOG)
  //    defaultSchema = configuration.getProperties.getProperty(DEFAULT_SCHEMA)
  //    configuration.getProperties.put(DIALECT, dialect)
  //
  //    // 1. first process class mapping
  //    val schemaSet = new collection.mutable.HashSet[String]
  //    mappings.classMappings.values foreach { pc =>
  //      val clazz = pc.getMappedClass
  //      val table = pc.getTable
  //      if (!isBlank(table.getSchema)) schemaSet += table.getSchema
  //      table.setComment(getComment(clazz, clazz.getSimpleName))
  //      commentIdProperty(clazz, table, pc.getIdentifierProperty, pc.getIdentifier)
  //      commentProperties(clazz, table, pc.getPropertyIterator)
  //
  //      if (isBlank(packageName) || clazz.getPackage.getName.startsWith(packageName)) {
  //        if (pc.isInstanceOf[RootClass]) {
  //          val ig = pc.getIdentifier.createIdentifierGenerator(
  //            configuration.getIdentifierGeneratorFactory, dialect, defaultCatalog, defaultSchema, pc.asInstanceOf[RootClass])
  //          if (ig.isInstanceOf[PersistentIdentifierGenerator]) {
  //            sequences ++= ig.asInstanceOf[PersistentIdentifierGenerator].sqlCreateStrings(dialect)
  //          }
  //        }
  //        generateTableSql(table)
  //      }
  //    }
  //
  //    // 2. process collection mapping
  //    val itercm = configuration.getCollectionMappings
  //    while (itercm.hasNext) {
  //      val col = itercm.next.asInstanceOf[Collection]
  //      if (isBlank(packageName) || col.getRole.startsWith(packageName)) {
  //        // collection sequences
  //        if (col.isIdentified) {
  //          val ig = col.asInstanceOf[IdentifierCollection].getIdentifier.createIdentifierGenerator(
  //            configuration.getIdentifierGeneratorFactory, dialect, defaultCatalog, defaultSchema, null)
  //
  //          if (ig.isInstanceOf[PersistentIdentifierGenerator]) {
  //            sequences ++= ig.asInstanceOf[PersistentIdentifierGenerator].sqlCreateStrings(dialect)
  //          }
  //        }
  //        // collection table
  //        if (!col.isOneToMany) {
  //          val table = col.getCollectionTable
  //          val owner = col.getTable.getComment
  //          if (!isBlank(table.getSchema)) schemaSet += table.getSchema
  //          var ownerClass = col.getOwner.getMappedClass
  //          // resolved nested compoent name in collection's role
  //          val colName = substringAfter(col.getRole, col.getOwnerEntityName + ".")
  //          if (colName.contains(".")) ownerClass = getPropertyType(col.getOwner, substringBeforeLast(colName, "."))
  //          table.setComment(owner + "-" + getComment(ownerClass, substringAfterLast(col.getRole, ".")))
  //
  //          val keyColumn = table.getColumn(col.getKey.getColumnIterator.next.asInstanceOf[Column])
  //          if (null != keyColumn) keyColumn.setComment(owner + "ID")
  //
  //          if (col.isInstanceOf[IndexedCollection]) {
  //            val idxCol = col.asInstanceOf[IndexedCollection]
  //            val idx = idxCol.getIndex
  //            if (idx.isInstanceOf[ToOne]) commentToOne(idx.asInstanceOf[ToOne], idx.getColumnIterator.next.asInstanceOf[Column])
  //          }
  //
  //          col.getElement match {
  //            case mto: ManyToOne =>
  //              val valueColumn = col.getElement.getColumnIterator.next.asInstanceOf[Column]
  //              commentToOne(mto, valueColumn)
  //            case cp: Component =>
  //              commentProperties(cp.getComponentClass, table, cp.getPropertyIterator)
  //            case _ =>
  //          }
  //          generateTableSql(col.getCollectionTable)
  //        }
  //      }
  //    }
  //    val newcomments = comments.toSet.toList
  //    comments.clear
  //    comments ++= newcomments
  //    schemas ++= schemaSet.map(s => s"create schema $s")
  //    schemas.sorted
  //
  //    // 3. export to files
  //    var total = 0
  //    files foreach {
  //      case (key, sqls) =>
  //        val sqlCount = sqls.foldLeft(0)((sum, l) => sum + l.size)
  //        total += sqlCount
  //        if (sqlCount > 0) {
  //          println(s"writing $sqlCount sqls to " + dirName + "/" + key)
  //          val writer = new FileWriter(dirName + "/" + key, false)
  //          writes(writer, sqls)
  //          writer.flush
  //          writer.close
  //        }
  //    }
  //    if (total == 0)
  //      println("Cannot find hibernate mapping files or classes,DDL generation aborted.")
  //  }
  //
  //  /**
  //   * get component class by component property string
  //   */
  //  private def getPropertyType(pc: PersistentClass, propertyString: String): Class[_] = {
  //    val properties = split(propertyString, '.')
  //    var p = pc.getProperty(properties(0))
  //    var cp = p.getValue.asInstanceOf[Component]
  //    var i = 1
  //    while (i < properties.length) {
  //      p = cp.getProperty(properties(i))
  //      cp = p.getValue.asInstanceOf[Component]
  //      i += 1
  //    }
  //    cp.getComponentClass
  //  }
  //
  //  private def commentToOne(toOne: ToOne, column: Column): Unit = {
  //    val entityName = toOne.getReferencedEntityName
  //    val referClass = configuration.getClassMapping(entityName)
  //    if (null != referClass) {
  //      column.setComment(referClass.getTable.getComment + "ID")
  //    }
  //  }
  //
  //  private def commentIdProperty(clazz: Class[_], table: Table, p: Property, identifier: KeyValue): Unit = {
  //    if (p.getColumnSpan == 1) {
  //      val column = p.getColumnIterator.next.asInstanceOf[Column]
  //      var comment = getComment(clazz, p.getName)
  //      identifier match {
  //        case sv: SimpleValue => comment += (":" + sv.getIdentifierGeneratorStrategy + toString(sv.getIdentifierGeneratorProperties))
  //        case _ =>
  //      }
  //      column.setComment(comment)
  //    } else if (p.getColumnSpan > 1) {
  //      val pc = p.getValue.asInstanceOf[Component]
  //      val columnOwnerClass = pc.getComponentClass
  //      commentProperties(columnOwnerClass, table, pc.getPropertyIterator)
  //    }
  //  }
  //
  //  private def toString(properties: ju.Properties): String = {
  //    if (properties.isEmpty) return ""
  //    val result = new collection.mutable.HashMap[String, String]
  //    val iter = properties.propertyNames
  //    while (iter.hasMoreElements) {
  //      val p = iter.nextElement.asInstanceOf[String]
  //      val value = properties.getProperty(p)
  //      if (null != value) result.put(p, value)
  //    }
  //    if (result.isEmpty) "" else result.toString.replace("Map", "")
  //  }
  //  private def commentProperty(clazz: Class[_], table: Table, p: Property): Unit = {
  //    if (null == p) return
  //    if (p.getColumnSpan == 1) {
  //      val column = p.getColumnIterator.next.asInstanceOf[Column]
  //      if (isForeignColumn(table, column)) {
  //        column.setComment(getComment(clazz, p.getName) + "ID")
  //      } else {
  //        column.setComment(getComment(clazz, p.getName))
  //      }
  //    } else if (p.getColumnSpan > 1) {
  //      val pc = p.getValue.asInstanceOf[Component]
  //      val columnOwnerClass = pc.getComponentClass
  //      commentProperties(columnOwnerClass, table, pc.getPropertyIterator)
  //    }
  //  }
  //
  //  private def commentProperties(clazz: Class[_], table: Table, ip: ju.Iterator[_]) {
  //    while (ip.hasNext)
  //      commentProperty(clazz, table, ip.next.asInstanceOf[Property])
  //  }
  //
  //  private def generateTableSql(table: Table): Unit = {
  //    if (!table.isPhysicalTable) return
  //    val commentIter = table.sqlCommentStrings(dialect, defaultCatalog, defaultSchema)
  //    while (commentIter.hasNext) comments += commentIter.next.toString
  //
  //    if (processed.contains(table)) return
  //    processed.add(table)
  //    tables += table.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
  //
  //    val subIter = table.getUniqueKeyIterator
  //    while (subIter.hasNext) {
  //      val uk = subIter.next
  //      val constraintString = uk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
  //      if (constraintString != null) constraints += constraintString
  //    }
  //
  //    val idxIter = table.getIndexIterator
  //    while (idxIter.hasNext) {
  //      val index = idxIter.next
  //      indexes += index.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
  //    }
  //
  //    if (dialect.hasAlterTable) {
  //      val fkIter = table.getForeignKeyIterator
  //      while (fkIter.hasNext) {
  //        val fk = fkIter.next.asInstanceOf[ForeignKey]
  //        if (fk.isPhysicalConstraint) {
  //          constraints += fk.sqlCreateString(dialect, mapping, defaultCatalog, defaultSchema)
  //        }
  //      }
  //    }
  //  }
  //
  //  private def isForeignColumn(table: Table, column: Column): Boolean = {
  //    val fkIter = table.getForeignKeyIterator
  //    while (fkIter.hasNext) {
  //      val fk = fkIter.next.asInstanceOf[ForeignKey]
  //      if (fk.isPhysicalConstraint) {
  //        if (fk.getColumns.contains(column)) return true
  //      }
  //    }
  //    return false
  //  }
  //
  //  private def getComment(clazz: Class[_], key: String): String = {
  //    val comment = messages.get(clazz, key)
  //    if (key == comment) {
  //      logger.warn(s"Cannot find comment of ${clazz.getName}.$key")
  //      key + "?"
  //    } else {
  //      comment
  //    }
  //  }
  //  private def writes(writer: Writer, contentList: List[collection.mutable.ListBuffer[String]]): Unit = {
  //    for (contents <- contentList) {
  //      for (script <- contents.sorted) {
  //        writer.write(script)
  //        writer.write(";\n")
  //      }
  //    }
  //  }
}