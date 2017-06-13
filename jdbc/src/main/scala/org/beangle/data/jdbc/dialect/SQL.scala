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
package org.beangle.data.jdbc.dialect

import org.beangle.data.jdbc.meta.Column
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.jdbc.meta.Sequence
import org.beangle.data.jdbc.meta.Index
import org.beangle.data.jdbc.meta.ForeignKey
import org.beangle.data.jdbc.meta.PrimaryKey

object SQL {

  def insert(table: Table): String = {
    val sb = new StringBuilder("insert into ")
    sb ++= table.qualifiedName
    sb += '('
    sb ++= table.quotedColumnNames.mkString(",")
    sb ++= ") values("
    sb ++= ("?," * table.columns.size)
    sb.setCharAt(sb.length() - 1, ')')
    sb.mkString
  }

  /**
   * Table creation sql
   */
  def createTable(table: Table, dialect: Dialect): String = {
    val grammar = dialect.tableGrammar
    val buf = new StringBuilder(grammar.createString).append(' ').append(table.qualifiedName).append(" (")
    val iter: Iterator[Column] = table.columns.iterator
    val l = table.columns.toList
    while (iter.hasNext) {
      val col: Column = iter.next()
      buf.append(col.literalName(dialect.engine)).append(' ')
      buf.append(col.sqlType.name)

      col.defaultValue foreach { dv =>
        buf.append(" default ").append(dv)
      }
      if (col.nullable) {
        buf.append(grammar.nullColumnString)
      } else {
        buf.append(" not null")
      }
      val useUniqueConstraint = col.unique && (!col.nullable || grammar.supportsNullUnique)
      if (useUniqueConstraint) {
        if (grammar.supportsUnique) {
          buf.append(" unique")
        } else {
          table.getOrCreateUniqueKey(col.literalName(dialect.engine) + '_').addColumn(col.name)
        }
      }

      if (col.hasCheck && grammar.supportsColumnCheck) {
        buf.append(" check (").append(col.check.get).append(")")
      }
      col.comment foreach { c =>
        buf.append(grammar.getColumnComment(c))
      }

      if (iter.hasNext) buf.append(", ")
    }
    table.primaryKey foreach { pk =>
      if (pk.enabled) {
        buf.append(", ").append(primaryKeySql(pk, dialect))
      }
    }
    buf.append(')')
    table.comment foreach { c =>
      buf.append(grammar.getComment(c))
    }

    buf.toString
  }

  def query(table: Table): String = {
    val sb: StringBuilder = new StringBuilder()
    sb.append("select ")
    for (columnName <- table.quotedColumnNames) {
      sb.append(columnName).append(',')
    }
    sb.deleteCharAt(sb.length() - 1)
    sb.append(" from ").append(table.qualifiedName)
    sb.toString()
  }

  def createSequence(seq: Sequence, dialect: Dialect): String = {
    if (null == dialect.sequenceGrammar) return null
    var sql: String = dialect.sequenceGrammar.createSql
    sql = sql.replace(":name", seq.qualifiedName)
    sql = sql.replace(":start", String.valueOf(seq.current + 1))
    sql = sql.replace(":increment", String.valueOf(seq.increment))
    sql = sql.replace(":cache", String.valueOf(seq.cache))
    sql = sql.replace(":cycle", if (seq.cycle) "cycle" else "")
    return sql
  }

  def dropSequence(seq: Sequence, dialect: Dialect): String = {
    if (null == dialect.sequenceGrammar) return null
    var sql: String = dialect.sequenceGrammar.dropSql;
    sql = sql.replace(":name", seq.qualifiedName)
    return sql
  }

  def createIndex(i: Index): String = {
    val buf = new StringBuilder("create")
      .append(if (i.unique) " unique" else "")
      .append(" index ")
      .append(i.literalName)
      .append(" on ")
      .append(i.table.qualifiedName)
      .append(" (");
    val iter = i.columns.iterator
    while (iter.hasNext) {
      buf.append(iter.next)
      if (iter.hasNext) buf.append(", ")
    }
    buf.append(")")
    buf.toString
  }

  def dropIndex(i: Index): String = {
    "drop index " + i.table.qualifiedName + "." + i.literalName
  }

  def alterTableAddforeignKey(fk: ForeignKey, dialect: Dialect): String = {
    require(null != fk.name && null != fk.table && null != fk.referencedTable)
    require(!fk.referencedColumns.isEmpty, " reference columns is empty.")
    require(!fk.columns.isEmpty, s"${fk.name} column's size should greate than 0")

    val engine = fk.table.engine
    val referencedColumnNames = fk.referencedColumns.map(x => x.toLiteral(engine)).toList
    val result = "alter table " + fk.table.qualifiedName + dialect.foreignKeySql(fk.literalName, fk.columnNames,
      fk.referencedTable.qualifiedName, referencedColumnNames)

    if (fk.cascadeDelete && dialect.supportsCascadeDelete) result + " on delete cascade" else result
  }

  def primaryKeySql(k: PrimaryKey, dialect: Dialect) = {
    val buf = new StringBuilder("primary key (")
    val engine = dialect.engine
    k.columns.foreach(col => (buf.append(col.toLiteral(engine)).append(", ")))
    if (!k.columns.isEmpty) buf.delete(buf.size - 2, buf.size);
    buf.append(')').result
  }
}
