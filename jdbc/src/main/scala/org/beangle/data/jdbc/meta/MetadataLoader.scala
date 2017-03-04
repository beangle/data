/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
package org.beangle.data.jdbc.meta

import java.sql.{ DatabaseMetaData, ResultSet, Statement }
import java.util.StringTokenizer
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable
import scala.collection.JavaConverters
import org.beangle.commons.lang.Strings.{ lowerCase, replace, upperCase }
import org.beangle.commons.lang.ThreadTasks
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.dialect.{ Dialect, MetadataGrammar, SequenceGrammar }
import org.beangle.commons.jdbc.Identifier
import org.beangle.commons.jdbc.Sequence
import org.beangle.commons.jdbc.Column
import org.beangle.commons.jdbc.Table
import org.beangle.commons.jdbc.PrimaryKey
import org.beangle.commons.jdbc.ForeignKey
import org.beangle.commons.jdbc.Index
import org.beangle.commons.jdbc.TableRef
import org.beangle.commons.jdbc.Schema
import org.beangle.commons.jdbc.SqlType

object MetadataColumns {
  val TableName = "TABLE_NAME"
  val ColumnName = "COLUMN_NAME"
  val ColumnSize = "COLUMN_SIZE"
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

  val PKTableSchem = "PKTABLE_SCHEM"
  val PKTableName = "PKTABLE_NAME"
  val PKColumnName = "PKCOLUMN_NAME"
  val PKName = "PK_NAME"

  val Remarks = "REMARKS"
}

class MetadataLoader(schema: Schema, initDialect: Dialect, initMeta: DatabaseMetaData) extends Logging {
  import MetadataColumns._

  val dialect: Dialect = initDialect
  val meta: DatabaseMetaData = initMeta
  val tables = new mutable.HashMap[String, Table]

  def loadTables(extras: Boolean): Unit = {
    val TYPES: Array[String] = Array("TABLE")
    val newCatalog = if (None == schema.catalog) null else schema.catalog.get.toLiteral(dialect.engine)
    val newSchema = schema.name.toLiteral(dialect.engine)

    val sw = new Stopwatch(true)
    var rs = meta.getTables(newCatalog, newSchema, null, TYPES)
    while (rs.next()) {
      val tableName = rs.getString(TableName)
      if (!tableName.startsWith("BIN$")) {
        val mySchema = schema.database.getOrCreateSchema(rs.getString(TableSchema))
        val table = new Table(mySchema, rs.getString(TableName))
        table.comment = Option(rs.getString(Remarks))
        tables.put(Table.qualify(table.schema, table.name), table)
      }
    }
    rs.close()
    logger.info(s"Load ${tables.size} tables in ${sw.toString}")

    // Loading columns
    sw.reset().start();
    rs = meta.getColumns(newCatalog, newSchema, "%", "%")
    var cols = 0
    import java.util.StringTokenizer
    while (rs.next()) {
      val colName = rs.getString(ColumnName)
      if (null != colName) {
        getTable(rs.getString(TableSchema), rs.getString(TableName)) foreach { table =>
          val length = rs.getInt(ColumnSize)
          val scale = rs.getInt(DecimalDigits)
          val sqlType = new SqlType(rs.getInt(DataType), new StringTokenizer(rs.getString(TypeName), "() ").nextToken(), length, scale)
          val nullable = "yes".equalsIgnoreCase(rs.getString(IsNullable))
          val col = new Column(Identifier(rs.getString(ColumnName)), sqlType, nullable)
          //          col.position = rs.getInt(OrdinalPosition)
          col.comment = Option(rs.getString(Remarks))
          table.add(col)
          cols += 1
        }
      }
    }
    rs.close()

    //evict empty column tables
    val origTabCount = tables.size
    tables.retain((name, table) => !table.columns.isEmpty)
    if (tables.size == origTabCount) logger.info(s"Load $cols columns in $sw")
    else logger.info(s"Load $cols columns and evict empty ${origTabCount - tables.size} tables in $sw.")

    if (extras) {
      if (null == dialect.metadataGrammar) {
        logger.info("Loading primary key,foreign key and index.")
        val tableNames = new ConcurrentLinkedQueue[String]
        tableNames.addAll(JavaConverters.asJavaCollection(tables.keySet.toList.sortWith(_ < _)))
        ThreadTasks.start(new MetaLoadTask(tableNames, tables), 5, "metaloader")
      } else {
        batchLoadExtra(schema, dialect.metadataGrammar)
      }
    }
  }

  private def batchLoadExtra(schema: Schema, grammar: MetadataGrammar) {
    val sw = new Stopwatch(true)
    var rs: ResultSet = null
    val schemaName = schema.name.toLiteral(dialect.engine)
    // load primary key
    rs = meta.getConnection().createStatement().executeQuery(grammar.primaryKeysql.replace(":schema", schemaName))
    while (rs.next()) {

      getTable(rs.getString(TableSchema), rs.getString(TableName)) foreach { table =>
        val colname = rs.getString(ColumnName)
        val pkName = getIdentifier(rs, PKName)
        table.primaryKey match {
          case None     => table.primaryKey = Some(new PrimaryKey(table, pkName, table.column(colname).name))
          case Some(pk) => pk.addColumn(table.column(colname))
        }
      }
    }
    rs.close()
    // load imported key
    rs = meta.getConnection().createStatement().executeQuery(grammar.importedKeySql.replace(":schema", schemaName))
    while (rs.next()) {
      getTable(rs.getString(FKTabkeSchem), rs.getString(FKTableName)) foreach { table =>
        val fkName = getIdentifier(rs, FKName)
        val column = table.column(rs.getString(FKColumnName))
        val fk = table.getForeignKey(fkName.value) match {
          case None       => table.add(new ForeignKey(table, getIdentifier(rs, FKName), column.name))
          case Some(oldk) => oldk
        }
        val pkSchema = schema.database.getOrCreateSchema(getIdentifier(rs, PKTableSchem))
        fk.refer(TableRef(pkSchema, getIdentifier(rs, PKTableName)), getIdentifier(rs, PKColumnName))
        fk.cascadeDelete = (rs.getInt(DeleteRule) != 3)
      }
    }
    rs.close()
    // load index
    rs = meta.getConnection().createStatement().executeQuery(grammar.indexInfoSql.replace(":schema", schemaName))
    while (rs.next()) {
      getTable(rs.getString(TableSchema), rs.getString(TableName)) foreach { table =>
        val indexName = rs.getString(IndexName)
        var idx = table.getIndex(indexName) match {
          case None         => table.add(new Index(table, Identifier(indexName)))
          case Some(oldIdx) => oldIdx
        }
        idx.unique = (rs.getBoolean("NON_UNIQUE") == false)
        val ascOrDesc = rs.getString("ASC_OR_DESC")
        if (null != ascOrDesc) idx.ascOrDesc = Some("A" == ascOrDesc)
        val columnName = rs.getString(ColumnName)
        //for oracle m_row$$ column
        table.getColumn(columnName) match {
          case Some(column) => idx.addColumn(column.name)
          case None         => idx.addColumn(Identifier(columnName))
        }
      }
    }
    rs.close()
    logger.info(s"Load contraint and index in $sw.")
  }

  class MetaLoadTask(val buffer: ConcurrentLinkedQueue[String], val tables: mutable.HashMap[String, Table]) extends Runnable {
    def run() {
      var completed = 0
      var nextTableName = buffer.poll()
      while (null != nextTableName) {
        try {
          val table = tables(nextTableName)
          logger.info(s"Loading ${table.qualifiedName}...")
          // load primary key
          var rs: ResultSet = null
          rs = meta.getPrimaryKeys(null, table.schema.name.value, table.name.value)
          var pk: PrimaryKey = null
          while (rs.next()) {
            val colnameName = Identifier(rs.getString(ColumnName))
            if (null == pk) pk = new PrimaryKey(table, Identifier(rs.getString(PKName)), colnameName)
            else pk.addColumn(colnameName)
          }
          if (null != pk) table.primaryKey = Some(pk)
          rs.close()
          // load imported key
          rs = meta.getImportedKeys(null, table.schema.name.value, table.name.value)
          while (rs.next()) {
            val fkName = rs.getString(FKName)
            val columnName = Identifier(rs.getString(FKColumnName))
            val fk = table.getForeignKey(fkName) match {
              case None       => table.add(new ForeignKey(table, Identifier(rs.getString(FKName)), columnName))
              case Some(oldk) => oldk
            }
            val pkSchema = schema.database.getOrCreateSchema(getIdentifier(rs, PKTableSchem))
            fk.refer(TableRef(pkSchema, getIdentifier(rs, PKTableName)), getIdentifier(rs, PKColumnName))
            fk.cascadeDelete = (rs.getInt(DeleteRule) != 3)
          }
          rs.close()
          // load index
          rs = meta.getIndexInfo(null, table.schema.name.value, table.name.value, false, true)
          while (rs.next()) {
            val index = rs.getString(IndexName)
            if (index != null) {
              val info = table.getIndex(index).getOrElse(table.add(new Index(table, getIdentifier(rs, IndexName))))
              info.unique = (rs.getBoolean("NON_UNIQUE") == false)
              val ascOrDesc = rs.getString("ASC_OR_DESC")
              if (null != ascOrDesc) info.ascOrDesc = Some("A" == ascOrDesc)
              info.addColumn(getIdentifier(rs, ColumnName))
            }
          }
          rs.close()
          table.schema.tables.put(Table.qualify(table.schema, table.name), table)
          completed += 1
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception                 => logger.error("Error in convertion ", e)
        }
        nextTableName = buffer.poll()
      }
      logger.info(s"${Thread.currentThread().getName} loaded $completed tables ")
    }
  }

  def loadSequences(): Unit = {
    val sequences = new mutable.HashSet[Sequence]
    val ss: SequenceGrammar = dialect.sequenceGrammar
    if (null == ss) return
    var sql: String = ss.querySequenceSql
    sql = replace(sql, ":schema", schema.name.toLiteral(dialect.engine))
    if (sql != null) {
      var statement: Statement = null
      var rs: ResultSet = null
      try {
        statement = meta.getConnection().createStatement()
        rs = statement.executeQuery(sql)
        val columnNames = new mutable.HashSet[String]
        for (i <- 1 to rs.getMetaData().getColumnCount()) {
          columnNames.add(rs.getMetaData().getColumnLabel(i).toLowerCase())
        }
        while (rs.next()) {
          val sequence = new Sequence(schema, getIdentifier(rs, "sequence_name"))
          if (columnNames.contains("current_value")) {
            sequence.current = java.lang.Long.valueOf(rs.getString("current_value")).longValue
          } else if (columnNames.contains("next_value")) {
            sequence.current = java.lang.Long.valueOf(rs.getString("next_value")).longValue - 1
          }
          if (columnNames.contains("increment_by")) {
            sequence.increment = (java.lang.Integer.valueOf(rs.getString("increment_by")).intValue).intValue
          }
          if (columnNames.contains("cache_size")) {
            sequence.cache = (java.lang.Integer.valueOf(rs.getString("cache_size"))).intValue
          }
          if (columnNames.contains("cycle_flag")) {
            val flag = rs.getString("cycle_flag").toLowerCase()
            sequence.cycle = (flag == "y" || flag == "yes" || flag == "on")
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

  private def getTable(schemaName: String, name: String): Option[Table] = {
    val tableSchema = schema.database.getOrCreateSchema(schemaName)
    tables.get(Table.qualify(tableSchema, Identifier(name)))
  }
}
