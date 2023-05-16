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

package org.beangle.data.jdbc.engine

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.meta.{Column, Index, Table}

import java.sql.Types.*

class SQLServer2005 extends AbstractEngine {

  this.registerReserved("t-sql.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar(MAX)", LONGVARCHAR -> "text",
    NCHAR -> "nchar($l)", NVARCHAR -> "nvarchar(MAX)", LONGNVARCHAR -> "ntext",
    BIT -> "bit", BOOLEAN -> "bit",
    TINYINT -> "smallint", SMALLINT -> "smallint", INTEGER -> "int", BIGINT -> "bigint",
    REAL -> "REAL", FLOAT -> "float", DOUBLE -> "double precision",
    DECIMAL -> "double precision", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "datetime2", TIMESTAMP_WITH_TIMEZONE -> "datetimeoffset",
    BINARY -> "binary", VARBINARY -> "varbinary(MAX)", LONGVARBINARY -> "varbinary(MAX)",
    BLOB -> "varbinary(MAX)", CLOB -> "varchar(MAX)", NCLOB -> "ntext",
    JAVA_OBJECT -> "nvarchar(MAX)")

  registerTypes2(
    (VARCHAR, 8000, "varchar($l)"),
    (VARBINARY, 8000, "varbinary($l)"),
    (NVARCHAR, 4000, "nvarchar($l)"))

  options.comment.supportsCommentOn = false
  options.sequence.supports = false

  options.table.alter { a =>
    a.changeType = "alter {column} {type}"
    a.setDefault = "add constraint {column}_dflt default {value} for {column}"
    a.dropDefault = "drop constraint {column}_dflt"
    a.setNotNull = "alter column {column} {type} not null"
    a.dropNotNull = "alter column {column} {type}"
    a.addColumn = "add {column} {type}"
    a.dropColumn = "drop column {column}"
    a.renameColumn = "EXEC sp_rename '{table}.{oldcolumn}', '{newcolumn}', 'COLUMN'"

    a.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.dropConstraint = "drop constraint {name}"
  }
  options.validate()

  override def limit(querySql: String, offset: Int, limit: Int): (String, List[Int]) = {
    if (null != options.limit.pattern) return super.limit(querySql, offset, limit)
    val sb = new StringBuilder(querySql)

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

  override def defaultSchema: String = "dbo"

  override def name: String = "Microsoft SQL Server"

  override def version: Version = Version("[9,10)")

  override def supportBoolean: Boolean = false

  functions { f =>
    f.currentDate = "convert(date,getdate())"
    f.localTime = "convert(time,getdate())"
    f.currentTime = "convert(time,getdate())"
    f.localTimestamp = "sysdatetime()"
    f.currentTimestamp = "sysdatetimeoffset()"
  }
}

class SQLServer2008 extends SQLServer2005 {
  options.limit { l =>
    l.pattern = "{} fetch first ? rows only"
    l.offsetPattern = "{} offset ? rows fetch next ? rows only"
    l.bindInReverseOrder = false
  }

  override def version: Version = Version("[10,11)")
}

class SQLServer2012 extends SQLServer2005 {
  options.limit { l =>
    l.pattern = "{} offset 0 rows fetch next ? rows only"
    l.offsetPattern = "{} offset ? rows fetch next ? rows only"
    l.bindInReverseOrder = false
  }

  override def version: Version = Version("[11,)")
}
