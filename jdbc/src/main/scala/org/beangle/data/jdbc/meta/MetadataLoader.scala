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

package org.beangle.data.jdbc.meta

import org.beangle.commons.collection.Collections
import org.beangle.commons.concurrent.Tasks
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.Strings.replace
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.engine.{Engine, Engines}
import org.beangle.data.jdbc.meta.Schema.NameFilter
import org.beangle.data.jdbc.query.JdbcExecutor

import java.sql.{Connection, DatabaseMetaData, ResultSet, Statement}
import java.util.concurrent.ConcurrentLinkedQueue
import javax.sql.DataSource
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asJava

object MetadataColumns {
  val TableName = "TABLE_NAME"
  val ColumnName = "COLUMN_NAME"
  val ColumnSize = "COLUMN_SIZE"
  val ColumnDef = "COLUMN_DEF"

  val TableCat = "TABLE_CAT"
  val TableSchema = "TABLE_SCHEM"
  val IndexName = "INDEX_NAME"
  val TypeName = "TYPE_NAME"
  val DataType = "DATA_TYPE"
  val IsNullable = "IS_NULLABLE"
  val OrdinalPosition = "ORDINAL_POSITION"
  val DecimalDigits = "DECIMAL_DIGITS"
  val DeleteRule = "DELETE_RULE"

  val FKColumnName = "FKCOLUMN_NAME"
  val FKName = "FK_NAME"
  val FKTabkeSchem = "FKTABLE_SCHEM"
  val FKTableName = "FKTABLE_NAME"

  val PKTableCat = "PKTABLE_CAT"
  val PKTableSchem = "PKTABLE_SCHEM"
  val PKTableName = "PKTABLE_NAME"
  val PKColumnName = "PKCOLUMN_NAME"
  val PKName = "PK_NAME"

  val Remarks = "REMARKS"
}

object MetadataLoader {
  def dump(meta: DatabaseMetaData, engine: Engine, catalog: Option[Identifier], schema: Option[Identifier]): Database = {
    val database = new Database(engine)

    val conn = meta.getConnection
    val loader = new MetadataLoader(conn.getMetaData, engine)
    val allFilter = new NameFilter()
    allFilter.include("*")

    val schemaNames = if schema.isEmpty then loader.schemas() else schema.map(_.value).toSet
    schemaNames foreach { s =>
      val schema = database.getOrCreateSchema(engine.toIdentifier(s))
      schema.catalog = catalog
      loader.loadTables(schema, allFilter, true)
      loader.loadViews(schema, allFilter)
      loader.loadSequences(schema)
    }
    database
  }

  def schemas(ds: DataSource): Seq[String] = {
    var conn: Connection = null
    try {
      conn = ds.getConnection
      val meta = conn.getMetaData
      val metadataLoader = new MetadataLoader(meta, Engines.forMetadata(meta))
      metadataLoader.schemas()
    } finally {
      IOs.close(conn)
    }
  }
}

class MetadataLoader(meta: DatabaseMetaData, engine: Engine) extends Logging {

  import MetadataColumns.*

  def schemas(): Seq[String] = {
    val names = Collections.newBuffer[String]
    if (engine.catalogAsSchema) {
      val rs = meta.getCatalogs
      while (rs.next()) {
        names.addOne(rs.getString(TableCat))
      }
      rs.close()
    } else {
      val rs = meta.getSchemas()
      while (rs.next()) {
        names.addOne(rs.getString(TableSchema))
      }
      rs.close()
    }
    //remove $ and system schemas
    val systemSchemas = engine.systemSchemas.map(_.toLowerCase).toSet
    val removed = names.filter { x =>
      val n = x.toLowerCase
      n.contains("$") || (!n.contains("_") && n.contains("sys")) || systemSchemas.contains(n)
    }
    names.subtractAll(removed)
    names.toSet.toSeq.sorted
  }

  def loadTables(schema: Schema, extras: Boolean): Unit = {
    val filter = new NameFilter
    filter.include("*")
    loadTables(schema, filter, extras)
  }

  def loadViews(schema: Schema): Unit = {
    val filter = new NameFilter
    filter.include("*")
    loadViews(schema, filter)
  }

  def loadViews(schema: Schema, filter: NameFilter): Unit = {
    val pattern = processCatalogSchema(schema)
    val catalogName = pattern._1
    val schemaPattern = pattern._2
    val sw = new Stopwatch(true)
    val rs = meta.getTables(catalogName, schemaPattern, null, Array("VIEW"))
    val views = new mutable.HashMap[String, View]
    while (rs.next()) {
      val tableName = rs.getString(TableName)
      if (!tableName.contains("$") && filter.isMatched(tableName)) {
        val view = schema.database.addView(getTableSchema(rs), rs.getString(TableName))
        view.updateCommentAndModule(rs.getString(Remarks))
        views.put(Table.qualify(view.schema, view.name), view)
      }
    }
    rs.close()
    logger.debug(s"Load ${views.size} views in ${sw.toString}")
    if (views.isEmpty) return
    val cols = loadColumns(schema.database, false, catalogName, schemaPattern)
    logger.debug(s"Load $cols columns in $sw")
    if (engine.metadataLoadSql.supportViewExtra) {
      batchLoadViewExtra(schema, engine.metadataLoadSql)
    }
  }

  def loadTables(schema: Schema, filter: NameFilter, extras: Boolean): Unit = {
    val pattern = processCatalogSchema(schema)
    val catalogName = pattern._1
    val schemaPattern = pattern._2
    val sw = new Stopwatch(true)
    val rs = meta.getTables(catalogName, schemaPattern, null, Array("TABLE"))
    val tables = new mutable.HashMap[String, Table]
    while (rs.next()) {
      val tableName = rs.getString(TableName)
      if (!tableName.contains("$") && filter.isMatched(tableName)) {
        val table = schema.database.addTable(getTableSchema(rs), rs.getString(TableName))
        table.updateCommentAndModule(rs.getString(Remarks))
        tables.put(Table.qualify(table.schema, table.name), table)
      }
    }
    rs.close()
    logger.debug(s"Load ${tables.size} tables in ${sw.toString}")

    if (tables.isEmpty) return

    val cols = loadColumns(schema.database, true, catalogName, schemaPattern)
    //evict empty column tables
    val origTabCount = tables.size
    schema.cleanEmptyTables()
    tables.filterInPlace((_, table) => table.columns.nonEmpty)
    if (tables.size == origTabCount) logger.debug(s"Load $cols columns in $sw")
    else logger.debug(s"Load $cols columns and evict empty ${origTabCount - tables.size} tables in $sw.")

    if (extras) {
      if (engine.metadataLoadSql.supportsTableExtra) {
        batchLoadTableExtra(schema, engine.metadataLoadSql)
      } else {
        logger.debug("Loading primary key,foreign key and index.")
        val tableNames = new ConcurrentLinkedQueue[String]
        tableNames.addAll(asJava(tables.keySet.toList.sortWith(_ < _)))
        Tasks.start(new ExtraMetaLoadTask(tableNames, tables), 5)
      }
    }
  }

  private def loadColumns(database: Database, isTable: Boolean, catalogName: String, schemaPattern: String): Int = {
    // Loading columns
    val sw = new Stopwatch(true)
    val rs = meta.getColumns(catalogName, schemaPattern, "%", "%")
    var cols = 0
    val types = Collections.newMap[String, SqlType]
    import java.util.StringTokenizer
    while (rs.next()) {
      val defaultValue = rs.getString(ColumnDef) //should read first in oracle,my be it's long type
      val colName = rs.getString(ColumnName)
      if (null != colName) {
        val relation =
          if isTable then database.getTable(getTableSchema(rs), rs.getString(TableName))
          else database.getView(getTableSchema(rs), rs.getString(TableName))

        relation foreach { r =>
          val typename = new StringTokenizer(rs.getString(TypeName), "() ").nextToken()
          val typecode = engine.resolveCode(rs.getInt(DataType), typename)
          var length = rs.getInt(ColumnSize)
          var scale = rs.getInt(DecimalDigits)
          if (scale == -127 && length == 0 && SqlType.isNumberType(typecode)) { // in oracle view,it will return -127
            scale = 5
            length = 19
          }
          val key = s"$typecode-$typename-$length-$scale"
          val sqlType = types.getOrElseUpdate(key, engine.toType(typecode, length, scale))
          val nullable = "yes".equalsIgnoreCase(rs.getString(IsNullable))
          val col = new Column(Identifier(rs.getString(ColumnName)), sqlType, nullable)
          col.comment = Option(rs.getString(Remarks))
          if (null != defaultValue) {
            var dv = Strings.trim(defaultValue)
            if (Strings.isNotEmpty(dv) && dv.contains("::")) {
              dv = Strings.substringBefore(dv, "::")
            }
            if dv.nonEmpty && dv != "null" then col.defaultValue = Option(dv)
          }
          r.add(col)
          cols += 1
        }
      }
    }
    rs.close()
    cols
  }

  private def getTableSchema(rs: ResultSet): String = {
    rs.getString(if engine.catalogAsSchema then TableCat else TableSchema)
  }

  private def processCatalogSchema(schema: Schema): (String, String) = {
    var catalogName = if (null == schema.catalog || schema.catalog.isEmpty) null else schema.catalog.get.value
    var schemaPattern = schema.name.value
    if Strings.isBlank(catalogName) then catalogName = null
    if Strings.isBlank(schemaPattern) then schemaPattern = null

    if (null == catalogName && null != schemaPattern && engine.catalogAsSchema) {
      val t = catalogName
      catalogName = schemaPattern
      schemaPattern = t
    }
    (catalogName, schemaPattern)
  }

  private def batchLoadTableExtra(schema: Schema, sql: MetadataLoadSql): Unit = {
    val sw = new Stopwatch(true)
    var rs: ResultSet = null
    val schemaName = schema.name.toLiteral(engine)
    // load primary key
    rs = meta.getConnection.createStatement().executeQuery(sql.primaryKeySql.replace(":schema", schemaName))
    while (rs.next()) {
      schema.database.getTable(rs.getString(TableSchema), rs.getString(TableName)) foreach {
        table =>
          val colname = rs.getString(ColumnName)
          val pkName = getIdentifier(rs, PKName)
          table.primaryKey match {
            case None => table.primaryKey = Some(new PrimaryKey(table, pkName, table.column(colname).name))
            case Some(pk) => pk.addColumn(table.column(colname))
          }
      }
    }
    rs.close()
    // load imported key
    rs = meta.getConnection.createStatement().executeQuery(sql.importedKeySql.replace(":schema", schemaName))
    while (rs.next()) {
      schema.database.getTable(rs.getString(FKTabkeSchem), rs.getString(FKTableName)) foreach {
        table =>
          val fkName = getIdentifier(rs, FKName)
          val column = table.column(rs.getString(FKColumnName))
          val fk = table.getForeignKey(fkName.value) match {
            case None => table.add(new ForeignKey(table, getIdentifier(rs, FKName), column.name))
            case Some(oldk) => oldk
          }
          val pkSchema = schema.database.getOrCreateSchema(getIdentifier(rs, PKTableSchem))
          fk.refer(TableRef(pkSchema, getIdentifier(rs, PKTableName)), getIdentifier(rs, PKColumnName))
          fk.cascadeDelete = rs.getInt(DeleteRule) != 3
      }
    }
    rs.close()
    // load index
    rs = meta.getConnection.createStatement().executeQuery(sql.indexInfoSql.replace(":schema", schemaName))
    while (rs.next()) {
      schema.database.getTable(rs.getString(TableSchema), rs.getString(TableName)) foreach { table =>
        val indexName = rs.getString(IndexName)
        if (!table.primaryKey.exists(_.name.value == indexName)) {
          val idx = table.getIndex(indexName) match {
            case None => table.add(new Index(table, Identifier(indexName)))
            case Some(oldIdx) => oldIdx
          }
          idx.unique = !rs.getBoolean("NON_UNIQUE")
          val columnName = rs.getString(ColumnName)
          //for oracle m_row$$ column
          table.getColumn(columnName) match {
            case Some(column) => idx.addColumn(column.name)
            case None => idx.addColumn(Identifier(columnName))
          }
        }
      }
    }
    rs.close()
    schema.tables.values foreach { t => t.convertIndexToUniqueKeys() }
    logger.debug(s"Load constraint and index in $sw.")
  }

  private def batchLoadViewExtra(schema: Schema, sql: MetadataLoadSql): Unit = {
    if (Strings.isBlank(engine.metadataLoadSql.viewDefSql)) return

    schema.views.values foreach { v =>
      var sql = engine.metadataLoadSql.viewDefSql
      sql = replace(sql, ":schema", schema.name.toLiteral(v.engine))
      sql = replace(sql, ":view_name", v.name.toLiteral(v.engine))
      var statement: Statement = null
      var rs: ResultSet = null
      try {
        statement = meta.getConnection.createStatement()
        rs = statement.executeQuery(sql)
        if (rs.next()) {
          val values = JdbcExecutor.convert(rs, JdbcExecutor.getTypes(rs, engine))
          if (values.nonEmpty && null != values(0)) {
            v.definition = Some(values(0).toString.trim())
          }
        }
      } finally {
        if (rs != null) rs.close()
        if (statement != null) statement.close()
      }
    }
  }

  private class ExtraMetaLoadTask(val buffer: ConcurrentLinkedQueue[String], val tables: mutable.HashMap[String, Table])
    extends Runnable {
    def run(): Unit = {
      var completed = 0
      var nextTableName = buffer.poll()
      while (null != nextTableName) {
        try {
          val table = tables(nextTableName)
          val schema = table.schema
          val catalogName = if engine.catalogAsSchema then table.schema.name.value else null
          val schemaName = if engine.catalogAsSchema then null else table.schema.name.value

          logger.debug(s"Loading ${table.qualifiedName}...")
          // load primary key
          var rs: ResultSet = null
          rs = meta.getPrimaryKeys(catalogName, schemaName, table.name.value)
          var pk: PrimaryKey = null
          while (rs.next()) {
            val colName = Identifier(rs.getString(ColumnName))
            if (null == pk) pk = new PrimaryKey(table, Identifier(rs.getString(PKName)), colName)
            else pk.addColumn(colName)
          }
          if (null != pk) table.primaryKey = Some(pk)
          rs.close()
          // load imported key
          rs = meta.getImportedKeys(catalogName, schemaName, table.name.value)
          while (rs.next()) {
            val fkName = rs.getString(FKName)
            val columnName = Identifier(rs.getString(FKColumnName))
            val fk = table.getForeignKey(fkName) match {
              case None => table.add(new ForeignKey(table, Identifier(rs.getString(FKName)), columnName))
              case Some(oldk) => oldk
            }
            val pkSchema = schema.database.getOrCreateSchema(getIdentifier(rs, if engine.catalogAsSchema then PKTableCat else PKTableSchem))
            fk.refer(TableRef(pkSchema, getIdentifier(rs, PKTableName)), getIdentifier(rs, PKColumnName))
            fk.cascadeDelete = rs.getInt(DeleteRule) != 3
          }
          rs.close()

          // load index
          rs = meta.getIndexInfo(catalogName, schemaName, table.name.value, false, true)
          while (rs.next()) {
            val index = rs.getString(IndexName)
            if (index != null && !table.isPrimaryKeyIndex(index)) {
              val info = table.getIndex(index).getOrElse(table.add(new Index(table, getIdentifier(rs, IndexName))))
              info.unique = !rs.getBoolean("NON_UNIQUE")
              info.addColumn(getIdentifier(rs, ColumnName))
            }
          }
          rs.close()
          completed += 1
          table.convertIndexToUniqueKeys()
        } catch {
          case _: IndexOutOfBoundsException =>
          case e: Exception => logger.error("Error in loading metadata ", e)
        }
        nextTableName = buffer.poll()
      }
      logger.debug(s"${Thread.currentThread().getName} loaded $completed tables")
    }
  }

  def loadSequences(schema: Schema): Unit = {
    val sequences = new mutable.HashSet[Sequence]
    if (Strings.isBlank(engine.metadataLoadSql.sequenceSql)) {
      return
    }
    var sql = engine.metadataLoadSql.sequenceSql
    sql = replace(sql, ":schema", schema.name.toLiteral(engine))
    if (sql != null) {
      var statement: Statement = null
      var rs: ResultSet = null
      try {
        statement = meta.getConnection.createStatement()
        rs = statement.executeQuery(sql)
        val columnNames = new mutable.HashSet[String]
        for (i <- 1 to rs.getMetaData.getColumnCount) {
          columnNames.add(rs.getMetaData.getColumnLabel(i).toLowerCase())
        }
        while (rs.next()) {
          val sequence = new Sequence(schema, getIdentifier(rs, "sequence_name"))
          if (columnNames.contains("current_value")) {
            sequence.current = java.lang.Long.valueOf(rs.getString("current_value")).longValue
          } else if (columnNames.contains("next_value")) {
            sequence.current = java.lang.Long.valueOf(rs.getString("next_value")).longValue - 1
          }
          if (columnNames.contains("increment_by")) {
            sequence.increment = java.lang.Integer.valueOf(rs.getString("increment_by")).intValue
          }
          if (columnNames.contains("cache_size")) {
            sequence.cache = java.lang.Integer.valueOf(rs.getString("cache_size")).intValue
          }
          if (columnNames.contains("cycle_flag")) {
            val flag = rs.getString("cycle_flag").toLowerCase()
            sequence.cycle = flag == "y" || flag == "yes" || flag == "on"
          }
          sequences += sequence
        }
      } finally {
        if (rs != null) rs.close()
        if (statement != null) statement.close()
      }
    }
    schema.sequences ++= sequences
  }

  private def getIdentifier(rs: ResultSet, columnName: String): Identifier = {
    Identifier(rs.getString(columnName))
  }

}
