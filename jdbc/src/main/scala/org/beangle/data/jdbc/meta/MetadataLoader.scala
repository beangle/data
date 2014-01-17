/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.meta

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.util.StringTokenizer

import scala.collection.mutable

import org.beangle.commons.lang.Strings.lowerCase
import org.beangle.commons.lang.Strings.replace
import org.beangle.commons.lang.Strings.upperCase
import org.beangle.commons.lang.ThreadTasks
import org.beangle.commons.lang.time.Stopwatch
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.dialect.SequenceGrammar

class MetadataLoader(initDialect: Dialect, initMeta: DatabaseMetaData) extends Logging {
  val dialect: Dialect = initDialect
  val meta: DatabaseMetaData = initMeta
  val tables = new mutable.HashMap[String, Table]

  def loadTables(catalog: String, schema: String, extras: Boolean): Set[Table] = {
    val TYPES: Array[String] = Array("TABLE")
    var newCatalog = catalog
    var newSchema = schema
    try {
      var rs: ResultSet = null
      try {
        if (meta.storesLowerCaseQuotedIdentifiers && meta.storesLowerCaseIdentifiers) {
          newCatalog = lowerCase(catalog)
          newSchema = lowerCase(schema)
        } else if (meta.storesUpperCaseQuotedIdentifiers && meta.storesUpperCaseIdentifiers) {
          newCatalog = upperCase(catalog)
          newSchema = upperCase(schema)
        }
        val sw = new Stopwatch(true)
        rs = meta.getTables(newCatalog, newSchema, null, TYPES)
        while (rs.next()) {
          val tableName = rs.getString("TABLE_NAME")
          if (!tableName.startsWith("BIN$")) {
            val table = new Table(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"));
            table.comment = rs.getString("REMARKS")
            tables.put(table.identifier, table)
          }
        }
        rs.close()
        logger.info("Load {} tables in {}", tables.size, sw)

        // Loading columns
        sw.reset().start();
        rs = meta.getColumns(newCatalog, newSchema, "%", "%")
        var cols = 0
        import java.util.StringTokenizer
        while (rs.next()) {
          val tableOpt = tables.get(Table.qualify(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME")))
          val colName = rs.getString("COLUMN_NAME")
          if (null != colName) {
            tableOpt match {
              case Some(table) =>
                val col = new Column(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"))
                col.position = rs.getInt("ORDINAL_POSITION")
                col.size = rs.getInt("COLUMN_SIZE")
                col.scale = rs.getShort("DECIMAL_DIGITS")
                col.nullable = "yes".equalsIgnoreCase(rs.getString("IS_NULLABLE"))
                col.typeName = new StringTokenizer(rs.getString("TYPE_NAME"), "() ").nextToken()
                col.comment = rs.getString("REMARKS")
                table.addColumn(col)
              case _ =>
            }
          }
          cols += 1
        }
        rs.close()

        //evict empty column tables
        val origTabCount = tables.size
        tables.retain((name, table) => !table.columns.isEmpty)
        if (tables.size == origTabCount) logger.info("Load {} columns in {},", cols, sw)
        else logger.info("Load {} columns and evict empty {} tables in {}.", Array(cols, (origTabCount - tables.size), sw))

        if (extras) {
          logger.info("Loading primary key,foreign key and index.")
          sw.reset().start()
          val tableNames = new mutable.ArrayBuffer[String] with mutable.SynchronizedBuffer[String]
          tableNames ++= tables.keySet.toList.sortWith(_ < _)
          ThreadTasks.start(new MetaLoadTask(tableNames, tables), 5, "metaloader")
          logger.info("Load contraint and index in {}.", sw)
        }
      } finally {
        if (rs != null) rs.close()
      }
    } catch {
      case e: SQLException => throw new RuntimeException(e)
    }
    tables.values.toSet
  }

  class MetaLoadTask(val buffer: mutable.Buffer[String], val tables: mutable.HashMap[String, Table]) extends Runnable {
    def run() {
      var completed = 0;
      while (!buffer.isEmpty) {
        try {
          val table = tables(buffer.remove(0))
          logger.info("Loading {}.", table)
          loadPrimaryKeys(table)
          loadTableForeignKeys(table)
          loadTableIndexes(table)
          completed += 1
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception => logger.error("Error in convertion ", e)
        }
      }
      logger.debug("{} load table {}", Thread.currentThread().getName(), completed)
    }
  }

  private def loadPrimaryKeys(table: Table) = {
    var rs: ResultSet = null
    try {
      val s = System.currentTimeMillis()
      rs = meta.getPrimaryKeys(null, table.schema, table.name)
      logger.debug("Load {}'s primary key in {}.", table.name, System.currentTimeMillis() - s)
      var pk: PrimaryKey = null
      while (rs.next()) {
        val colname = rs.getString("COLUMN_NAME")
        if (null == pk) pk = new PrimaryKey(rs.getString("PK_NAME"), table.getColumn(colname))
        else pk.addColumn(table.getColumn(colname))
      }
      if (null != pk) table.primaryKey = pk
    } finally {
      if (rs != null) rs.close()
    }
  }

  private def loadTableForeignKeys(table: Table) {
    var rs: ResultSet = null
    try {
      val s = System.currentTimeMillis()
      rs = meta.getImportedKeys(null, table.schema, table.name)
      logger.debug("Load {}'s foreign key in {}.", table.name, System.currentTimeMillis() - s)
      while (rs.next()) {
        val fk = rs.getString("FK_NAME")
        var info = table.getForeignKey(fk)
        if (null != fk && null == info) {
          info = new ForeignKey(rs.getString("FK_NAME"), table.getColumn(rs.getString("FKCOLUMN_NAME")))
          info.addReferencedColumn(new Column(rs.getString("PKCOLUMN_NAME"), Types.BIGINT))
          val referencedTable = tables.getOrElse(Table.qualify(rs.getString("PKTABLE_SCHEM"),
            rs.getString("PKTABLE_NAME")), new Table(rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME")))
          info.setReferencedTable(referencedTable)
          info.setCascadeDelete((rs.getInt("DELETE_RULE") != 3))
          table.addForeignKey(info)
        }
      }
    } finally {
      if (rs != null) rs.close()
    }
  }

  private def loadTableIndexes(table: Table) = {
    var rs: ResultSet = null
    try {
      val s = System.currentTimeMillis()
      rs = meta.getIndexInfo(null, table.schema, table.name, false, true)
      logger.debug("Load {}'s index in {}.", table.name, System.currentTimeMillis() - s)
      while (rs.next()) { // && (rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic)) {
        val index = rs.getString("INDEX_NAME")
        if (index != null) {
          var info = table.getIndex(index)
          if (info == null) {
            info = new Index(rs.getString("INDEX_NAME"), table)
            table.addIndex(info)
          }
          info.unique = (rs.getBoolean("NON_UNIQUE") == false)
          val ascOrDesc = rs.getString("ASC_OR_DESC")
          if (null != ascOrDesc) info.ascOrDesc = Some("A" == ascOrDesc)
          info.addColumn(table.getColumn(rs.getString("COLUMN_NAME")))
        }
      }
    } finally {
      if (rs != null) rs.close()
    }
  }

  def loadSequences(connection: Connection, schema: String): Set[Sequence] = {
    val sequences = new mutable.HashSet[Sequence]
    val ss: SequenceGrammar = dialect.sequenceGrammar
    if (null == ss) return Set.empty
    var sql: String = ss.querySequenceSql
    sql = replace(sql, ":schema", schema)
    if (sql != null) {
      var statement: Statement = null
      var rs: ResultSet = null
      try {
        statement = connection.createStatement()
        rs = statement.executeQuery(sql)
        val columnNames = new mutable.HashSet[String]
        for (i <- 1 to rs.getMetaData().getColumnCount()) {
          columnNames.add(rs.getMetaData().getColumnLabel(i).toLowerCase())
        }
        while (rs.next()) {
          val sequence = new Sequence(rs.getString("sequence_name").toLowerCase().trim())
          if (columnNames.contains("current_value")) {
            sequence.current = java.lang.Long.valueOf(rs.getString("current_value")).longValue
          } else if (columnNames.contains("next_value")) {
            sequence.current = java.lang.Long.valueOf(rs.getString("next_value")).longValue - 1
          }
          if (columnNames.contains("increment")) {
            sequence.increment = (java.lang.Integer.valueOf(rs.getString("increment")).intValue).intValue
          }
          if (columnNames.contains("cache")) {
            sequence.cache = (java.lang.Integer.valueOf(rs.getString("cache"))).intValue
          }
          sequences += sequence
        }
      } finally {
        if (rs != null) rs.close()
        if (statement != null) statement.close()
      }
    }
    sequences.toSet
  }
}
