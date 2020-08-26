/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.engine

import java.sql.Types._

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.{Column, Index, Table}

class SQLServer(v: String) extends AbstractEngine(Version(v)) {

  this.registerReserved("t-sql.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar(MAX)", NVARCHAR -> "nvarchar(MAX)",
    BIT -> "bit", BOOLEAN -> "bit",
    TINYINT -> "smallint", SMALLINT -> "smallint", INTEGER -> "int", BIGINT -> "bigint",
    FLOAT -> "float", DOUBLE -> "double precision",
    DECIMAL -> "double precision", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "datetime2",
    BINARY -> "binary", VARBINARY -> "varbinary(MAX)",
    LONGVARCHAR -> "text", LONGVARBINARY -> "varbinary(MAX)",
    BLOB -> "varbinary(MAX)", CLOB -> "varchar(MAX)")

  registerTypes2(
    (VARCHAR, 8000, "varchar($l)"),
    (VARBINARY, 8000, "varbinary($l)"),
    (NVARCHAR, 4000, "nvarchar($l)"))

  options.comment.supportsCommentOn = false
  options.sequence.supports = false

  options.alter { a =>
    a.table.changeType = "alter {column} {type}"
    a.table.setDefault = "add constraint {column}_dflt default {value} for {column}"
    a.table.dropDefault = "drop constraint {column}_dflt"
    a.table.setNotNull = "alter column {column} {type} not null"
    a.table.dropNotNull = "alter column {column} {type}"
    a.table.addColumn = "add {column} {type}"
    a.table.dropColumn = "drop column {column}"
    a.table.renameColumn = "EXEC sp_rename '{table}.{oldcolumn}', '{newcolumn}', 'COLUMN'"

    a.table.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.table.dropConstraint = "drop constraint {name}"
  }
  options.validate()

  override def limit(querySql: String, offset: Int, limit: Int): (String, List[Int]) = {
    val sb: StringBuilder = new StringBuilder(querySql)

    val orderByIndex: Int = querySql.toLowerCase().indexOf("order by")
    var orderby: CharSequence = "order by current_timestmap"
    if (orderByIndex > 0) orderby = sb.subSequence(orderByIndex, sb.length())

    // Delete the order by clause at the end of the query
    if (orderByIndex > 0) {
      sb.delete(orderByIndex, orderByIndex + orderby.length())
    }

    // HHH-5715 bug fix
    replaceDistinctWithGroupBy(sb)

    insertRowNumberFunction(sb, orderby)

    // Wrap the query within a with statement:
    sb.insert(0, "with query as (").append(") select * from query ")
    sb.append("where _rownum_ between ? and ?")

    (sb.toString(), List(offset + 1, offset + limit))
  }

  override def alterTableRenameColumn(table: Table, col: Column, newName: String): String = {
    var renameClause = options.alter.table.renameColumn
    renameClause = Strings.replace(renameClause, "{oldcolumn}", col.name.toLiteral(table.engine))
    renameClause = Strings.replace(renameClause, "{newcolumn}", newName)
    renameClause = Strings.replace(renameClause, "{table}", table.qualifiedName)
    renameClause
  }

  protected def replaceDistinctWithGroupBy(sql: StringBuilder): Unit = {
    val distinctIndex = sql.indexOf("distinct")
    if (distinctIndex > 0) {
      sql.delete(distinctIndex, distinctIndex + "distinct".length() + 1)
      sql.append(" group by").append(getSelectFieldsWithoutAliases(sql))
    }
  }

  protected def insertRowNumberFunction(sql: StringBuilder, orderby: CharSequence): Unit = {
    // Find the start of the from statement
    val fromIndex = sql.toString().toLowerCase().indexOf("from")
    // Insert before the from statement the row_number() function:
    sql.insert(fromIndex, ",ROW_NUMBER() OVER (" + orderby + ") as _rownum_ ")
  }

  protected def getSelectFieldsWithoutAliases(sql: StringBuilder): String = {
    val select = sql.substring(sql.indexOf("select") + "select".length(), sql.indexOf("from"))
    // Strip the as clauses
    stripAliases(select)
  }

  protected def stripAliases(str: String): String = {
    str.replaceAll("\\sas[^,]+(,?)", "$1")
  }

  override def dropIndex(i: Index): String = {
    "drop index " + i.table.qualifiedName + "." + i.literalName
  }

  override def defaultSchema: String = {
    "dbo"
  }

  override def name: String = "Microsoft SQL Server"
}
