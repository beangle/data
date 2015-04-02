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
package org.beangle.data.conversion.db

import org.beangle.commons.collection.page.PageLimit
import org.beangle.commons.logging.Logging
import org.beangle.data.conversion.DataWrapper
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.meta.Database
import org.beangle.data.jdbc.meta.Sequence
import org.beangle.data.jdbc.meta.Table
import org.beangle.data.jdbc.query.JdbcExecutor

import javax.sql.DataSource

class DatabaseWrapper(val dataSource: DataSource, val dialect: Dialect, val catalog: String, val schema: String)
  extends DataWrapper with Logging {

  val database = new Database(dataSource.getConnection().getMetaData(), dialect, catalog, schema)
  val executor = new JdbcExecutor(dataSource)
  protected var productName: String = _

  def drop(table: Table): Boolean = {
    try {
      val tablename = Table.qualify(database.schema, table.name)
      val existed = database.tables.get(tablename)
      if (existed.isDefined) {
        database.tables.remove(tablename)
        executor.update(database.dialect.tableGrammar.dropCascade(tablename))
      }
    } catch {
      case e: Exception =>
        logger.error(s"Drop table ${table.name} failed", e)
        return false
    }
    return true
  }

  def create(table: Table): Boolean = {
    if (database.getTable(table.identifier).isEmpty) {
      try {
        executor.update(table.createSql(database.dialect))
      } catch {
        case e: Exception =>
          logger.error(s"Cannot create table ${table.name}", e)
          return false
      }
    }
    true
  }

  def drop(sequence: Sequence): Boolean = {
    val exists = database.sequences.contains(sequence)
    if (exists) {
      database.sequences.remove(sequence)
      try {
        val dropSql = sequence.dropSql(dialect)
        if (null != dropSql) executor.update(dropSql)
      } catch {
        case e: Exception =>
          logger.error(s"Drop sequence ${sequence.name} failed", e)
          return false
      }
    }
    true
  }

  def create(sequence: Sequence): Boolean = {
    try {
      val createSql = sequence.createSql(dialect)
      if (null != createSql) executor.update(createSql)
    } catch {
      case e: Exception =>
        logger.error(s"cannot create sequence ${sequence.name}", e)
        return false
    }
    return true
  }

  def count(table: Table): Int = executor.queryForInt("select count(*) from (" + table.querySql + ") tb" + System.currentTimeMillis())

  def get(table: Table, limit: PageLimit): Seq[Seq[_]] = {
    val orderBy = new StringBuffer

    if (null != table.primaryKey && table.primaryKey.columns.length > 0) {
      orderBy.append(" order by ")
      orderBy.append(table.primaryKey.columns.foldLeft("")(_ + "," + _).substring(1))
    }

    val sql = table.querySql + orderBy.toString
    val grammar = database.dialect.limitGrammar
    val limitSql = grammar.limit(sql, limit.pageIndex > 1)

    val offset = (limit.pageIndex - 1) * limit.pageSize
    val limitOrMax = if (grammar.useMax) limit.pageIndex * limit.pageSize else limit.pageSize

    if (limit.pageIndex == 1) {
      return executor.query(limitSql, limitOrMax)
    } else {
      if (grammar.bindInReverseOrder) return executor.query(limitSql, limitOrMax, offset)
      else executor.query(limitSql, offset, limitOrMax)
    }
  }

  def get(table: Table): Seq[Seq[_]] = executor.query(table.querySql)

  def save(table: Table, datas: Seq[Seq[_]]): Int = {
    val types = for (name <- table.columnNames) yield table.column(name).typeCode
    val insertSql = table.insertSql
    executor.batch(insertSql, datas, types).length
  }

  def supportLimit = (null != dialect.limitGrammar)

  def close() {}
}