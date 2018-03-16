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
package org.beangle.data.jdbc.dialect

import java.sql.Types._
import org.beangle.data.jdbc.meta.Engines

class SQLServerDialect(version: String) extends AbstractDialect(Engines.SQLServer, version) {

  val SELECT: String = "select"
  val FROM: String = "from"
  val DISTINCT: String = "distinct"

  def this() {
    this("[2005,2012)")
  }

  override def defaultSchema: String = {
    "dbo"
  }
  override def sequenceGrammar: SequenceGrammar = null

  override def limitGrammar: LimitGrammar = {
    class SqlServerLimitGrammar extends LimitGrammar {
      override def limit(querySql: String, offset: Int, limit: Int): Tuple2[String, List[Int]] = {
        val sb: StringBuilder = new StringBuilder(querySql)

        val orderByIndex: Int = querySql.toLowerCase().indexOf("order by")
        var orderby: CharSequence = "ORDER BY CURRENT_TIMESTAMP";
        if (orderByIndex > 0) orderby = sb.subSequence(orderByIndex, sb.length())

        // Delete the order by clause at the end of the query
        if (orderByIndex > 0) {
          sb.delete(orderByIndex, orderByIndex + orderby.length())
        }

        // HHH-5715 bug fix
        replaceDistinctWithGroupBy(sb)

        insertRowNumberFunction(sb, orderby)

        // Wrap the query within a with statement:
        sb.insert(0, "WITH query AS (").append(") SELECT * FROM query ")
        sb.append("WHERE _row_nr_ BETWEEN ? AND ?")

        (sb.toString(), List(offset + 1, offset + limit))
      }
    }
    new SqlServerLimitGrammar
  }

  protected def replaceDistinctWithGroupBy(sql: StringBuilder) = {
    val distinctIndex = sql.indexOf(DISTINCT)
    if (distinctIndex > 0) {
      sql.delete(distinctIndex, distinctIndex + DISTINCT.length() + 1)
      sql.append(" group by").append(getSelectFieldsWithoutAliases(sql))
    }
  }

  protected def insertRowNumberFunction(sql: StringBuilder, orderby: CharSequence) {
    // Find the end of the select statement
    val selectEndIndex = sql.indexOf(SELECT) + SELECT.length()
    // Insert after the select statement the row_number() function:
    sql.insert(selectEndIndex, " ROW_NUMBER() OVER (" + orderby + ") as _row_nr_,")
  }

  protected def getSelectFieldsWithoutAliases(sql: StringBuilder) = {
    val select = sql.substring(sql.indexOf(SELECT) + SELECT.length(), sql.indexOf(FROM))
    // Strip the as clauses
    stripAliases(select)
  }

  protected def stripAliases(str: String) = str.replaceAll("\\sas[^,]+(,?)", "$1")
}
