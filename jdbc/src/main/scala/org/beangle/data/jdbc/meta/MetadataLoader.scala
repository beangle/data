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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
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
import org.beangle.data.jdbc.query.JdbcExecutor
import org.beangle.data.jdbc.dialect.MetadataGrammar
import java.util.concurrent.ConcurrentLinkedQueue

class MetadataLoader(initDialect: Dialect, initMeta: DatabaseMetaData) extends Logging {
  val dialect: Dialect = initDialect
  val meta: DatabaseMetaData = initMeta
  val tables = new mutable.HashMap[String, Table]

  def loadTables(catalog: String, schema: String, extras: Boolean): Set[Table] = {
    val TYPES: Array[String] = Array("TABLE")
    var newCatalog = catalog
    var newSchema = schema
    var rs: ResultSet = null
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
    info(s"Load ${tables.size} tables in ${sw.toString}")

    // Loading columns
    sw.reset().start();
    rs = meta.getColumns(newCatalog, newSchema, "%", "%")
    var cols = 0
    import java.util.StringTokenizer
    while (rs.next()) {
      val colName = rs.getString("COLUMN_NAME")
      if (null != colName) {
        tables.get(Table.qualify(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"))) foreach { table =>
          val col = new Column(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"))
          col.position = rs.getInt("ORDINAL_POSITION")
          col.size = rs.getInt("COLUMN_SIZE")
          col.scale = rs.getShort("DECIMAL_DIGITS")
          col.nullable = "yes".equalsIgnoreCase(rs.getString("IS_NULLABLE"))
          col.typeName = new StringTokenizer(rs.getString("TYPE_NAME"), "() ").nextToken()
          col.comment = rs.getString("REMARKS")
          table.addColumn(col)
        }
      }
      cols += 1
    }
    rs.close()

    //evict empty column tables
    val origTabCount = tables.size
    tables.retain((name, table) => !table.columns.isEmpty)
    if (tables.size == origTabCount) info(s"Load $cols columns in $sw")
    else info(s"Load $cols columns and evict empty ${origTabCount - tables.size} tables in $sw.")

    if (extras) {
      if (null == dialect.metadataGrammar) {
        info("Loading primary key,foreign key and index.")
        val tableNames = new ConcurrentLinkedQueue[String]
        tableNames.addAll(collection.JavaConversions.asJavaCollection(tables.keySet.toList.sortWith(_ < _)))
        ThreadTasks.start(new MetaLoadTask(tableNames, tables), 5, "metaloader")
      } else {
        batchLoadExtra(schema, dialect.metadataGrammar)
      }
    }
    tables.values.toSet
  }

  private def batchLoadExtra(schema: String, grammar: MetadataGrammar) {
    val sw = new Stopwatch(true)
    var rs: ResultSet = null
    // load primary key
    rs = meta.getConnection().createStatement().executeQuery(grammar.primaryKeysql.replace(":schema", schema))
    while (rs.next()) {
      val t = tables.get(Table.qualify(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"))).orNull
      tables.get(Table.qualify(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"))) foreach { table =>
        val colname = rs.getString("COLUMN_NAME")
        val pkName = rs.getString("PK_NAME")
        if (null == table.primaryKey) table.primaryKey = new PrimaryKey(pkName, table.column(colname))
        else table.primaryKey.addColumn(table.column(colname))
      }
    }
    rs.close()
    // load imported key
    rs = meta.getConnection().createStatement().executeQuery(grammar.importedKeySql.replace(":schema", schema))
    while (rs.next()) {
      tables.get(Table.qualify(rs.getString("FKTABLE_SCHEM"), rs.getString("FKTABLE_NAME"))) foreach { table =>
        val fkName = rs.getString("FK_NAME")
        val column = table.column(rs.getString("FKCOLUMN_NAME"))
        val fk = table.getForeignKey(fkName) match {
          case None => {
            val newfk = new ForeignKey(rs.getString("FK_NAME"), column)
            table.addForeignKey(newfk)
            newfk
          }
          case Some(oldk) => oldk
        }
        fk.addReferencedColumn(new Column(rs.getString("PKCOLUMN_NAME"), column.typeCode))
        val referencedTable = tables.getOrElse(Table(rs.getString("PKTABLE_SCHEM"),
          rs.getString("PKTABLE_NAME")), new Table(rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME")))
        fk.setReferencedTable(referencedTable)
        fk.setCascadeDelete((rs.getInt("DELETE_RULE") != 3))
      }
    }
    rs.close()
    // load index
    rs = meta.getConnection().createStatement().executeQuery(grammar.indexInfoSql.replace(":schema", schema))
    while (rs.next()) {
      tables.get(Table.qualify(rs.getString("TABLE_SCHEM"), rs.getString("TABLE_NAME"))) foreach { table =>
        val indexName = rs.getString("INDEX_NAME")
        var idx = table.getIndex(indexName) match {
          case None => {
            val newIndex = new Index(indexName, table)
            table.addIndex(newIndex)
            newIndex
          }
          case Some(oldIdx) => oldIdx
        }
        idx.unique = (rs.getBoolean("NON_UNIQUE") == false)
        val ascOrDesc = rs.getString("ASC_OR_DESC")
        if (null != ascOrDesc) idx.ascOrDesc = Some("A" == ascOrDesc)
        val columnName = rs.getString("COLUMN_NAME")
        //for oracle m_row$$ column
        val column = table.getColumn(columnName) match {
          case Some(column) => column
          case None => new Column(columnName, 0)
        }
        idx.addColumn(column)
      }
    }
    rs.close()
    info(s"Load contraint and index in $sw.")
  }

  class MetaLoadTask(val buffer: ConcurrentLinkedQueue[String], val tables: mutable.HashMap[String, Table]) extends Runnable {
    def run() {
      var completed = 0
      var nextTableName = buffer.poll()
      while (null != nextTableName) {
        try {
          val table = tables(nextTableName)
          info(s"Loading $table.")
          // load primary key
          var rs: ResultSet = null
          rs = meta.getPrimaryKeys(null, table.schema, table.name)
          var pk: PrimaryKey = null
          while (rs.next()) {
            val colname = rs.getString("COLUMN_NAME")
            if (null == pk) pk = new PrimaryKey(rs.getString("PK_NAME"), table.column(colname))
            else pk.addColumn(table.column(colname))
          }
          if (null != pk) table.primaryKey = pk
          rs.close()
          // load imported key
          rs = meta.getImportedKeys(null, table.schema, table.name)
          while (rs.next()) {
            val fkName = rs.getString("FK_NAME")
            val column = table.column(rs.getString("FKCOLUMN_NAME"))
            val fk = table.getForeignKey(fkName) match {
              case None => {
                val newfk = new ForeignKey(rs.getString("FK_NAME"), column)
                table.addForeignKey(newfk)
                newfk
              }
              case Some(oldk) => oldk
            }
            fk.addReferencedColumn(new Column(rs.getString("PKCOLUMN_NAME"), column.typeCode))
            val referencedTable = tables.getOrElse(Table.qualify(rs.getString("PKTABLE_SCHEM"),
              rs.getString("PKTABLE_NAME")), new Table(rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME")))
            fk.setReferencedTable(referencedTable)
            fk.setCascadeDelete((rs.getInt("DELETE_RULE") != 3))
          }
          rs.close()
          // load index
          rs = meta.getIndexInfo(null, table.schema, table.name, false, true)
          while (rs.next()) { // && (rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic)) {
            val index = rs.getString("INDEX_NAME")
            if (index != null) {
              var info = table.getIndex(index).orNull
              if (info == null) {
                info = new Index(rs.getString("INDEX_NAME"), table)
                table.addIndex(info)
              }
              info.unique = (rs.getBoolean("NON_UNIQUE") == false)
              val ascOrDesc = rs.getString("ASC_OR_DESC")
              if (null != ascOrDesc) info.ascOrDesc = Some("A" == ascOrDesc)
              info.addColumn(table.column(rs.getString("COLUMN_NAME")))
            }
          }
          rs.close()
          completed += 1
        } catch {
          case e: IndexOutOfBoundsException =>
          case e: Exception => error("Error in convertion ", e)
        }
        nextTableName = buffer.poll()
      }
      info(s"${Thread.currentThread().getName()} loaded $completed tables ")
    }
  }

  def loadSequences(schema: String): Set[Sequence] = {
    val sequences = new mutable.HashSet[Sequence]
    val ss: SequenceGrammar = dialect.sequenceGrammar
    if (null == ss) return Set.empty
    var sql: String = ss.querySequenceSql
    sql = replace(sql, ":schema", schema)
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
          val sequence = new Sequence(rs.getString("sequence_name").toLowerCase().trim())
          sequence.schema = schema
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
