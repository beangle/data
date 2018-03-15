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

class OracleDialect() extends AbstractDialect(Engines.Oracle, "[10.1)") {

  override def limitGrammar: LimitGrammar = {

    /**
     * FIXME distinguish sql with order by or not
     * @see http://blog.csdn.net/czp11210/article/details/23958065
     */
    class OracleLimitGrammar extends LimitGrammar {
      override def limit(querySql: String, offset: Int, limit: Int): Tuple2[String, List[Int]] = {
        var sql = querySql.trim()
        var isForUpdate = false
        if (sql.toLowerCase().endsWith(" for update")) {
          sql = sql.substring(0, sql.length - 11)
          isForUpdate = true
        }
        val pagingSelect = new StringBuilder(sql.length + 100)
        val hasOffset = offset > 0
        if (hasOffset) pagingSelect.append("select * from ( select row_.*, rownum rownum_ from ( ")
        else pagingSelect.append("select * from ( ")

        pagingSelect.append(sql)
        if (hasOffset) pagingSelect.append(" ) row_ where rownum <= ?) where rownum_ > ?")
        else pagingSelect.append(" ) where rownum <= ?")

        if (isForUpdate) pagingSelect.append(" for update")
        (pagingSelect.toString, if (hasOffset) List(limit + offset, offset) else List(limit))
      }
    }
    new OracleLimitGrammar
  }

  override def sequenceGrammar = {
    val ss = new SequenceGrammar();
    ss.querySequenceSql = "select sequence_name,last_number as next_value,increment_by,cache_size,cycle_flag " +
      "from all_sequences where sequence_owner=':schema'"
    ss.createSql = "create sequence :name increment by :increment start with :start cache :cache :cycle"
    ss.nextValSql = "select :name.nextval from dual"
    ss.selectNextValSql = ":name.nextval"
    ss
  }

  override def defaultSchema: String = {
    "$user"
  }

  override def metadataGrammar: MetadataGrammar = {
    new MetadataGrammar(
      "select con.constraint_name PK_NAME,con.owner TABLE_SCHEM,con.table_name TABLE_NAME,col.column_name COLUMN_NAME" +
        " from user_constraints con,user_cons_columns col" +
        " where con.constraint_type='P'and con.constraint_name=col.constraint_name and col.owner=con.owner" +
        " and con.owner=':schema'",

      "select mycon.constraint_name FK_NAME,mycon.owner FKTABLE_SCHEM,mycon.table_name FKTABLE_NAME,mycol.column_name FKCOLUMN_NAME," +
        " case when mycon.delete_rule='CASCADE' then 0 " +
        "  when mycon.delete_rule='RESTRICT' then 1 " +
        "  when mycon.delete_rule='SET NULL' then 2 " +
        "  when mycon.delete_rule='NO ACTION' then 3 " +
        "  else 4  end " +
        " DELETE_RULE,rcon.owner PKTABLE_SCHEM,rcon.table_name PKTABLE_NAME,rcol.column_name PKCOLUMN_NAME " +
        " from user_constraints mycon,user_cons_columns mycol,user_constraints rcon,user_cons_columns rcol " +
        " where mycon.constraint_type='R' " +
        " and mycon.constraint_name=mycol.constraint_name and rcon.constraint_name=rcol.constraint_name " +
        " and mycol.owner=mycon.owner and rcon.owner=rcol.owner" +
        " and mycon.r_constraint_name=rcon.constraint_name " +
        " and mycon.owner=':schema' ",

      "select idx.INDEX_NAME,idx.table_owner TABLE_SCHEM,idx.table_name TABLE_NAME, case when uniqueness ='UNIQUE' then 0 else 1 end as NON_UNIQUE," +
        " col.COLUMN_NAME,case when col.descend ='ASC' then  'A' else  'D' end as ASC_OR_DESC,col.column_position ORDINAL_POSITION" +
        " from user_indexes idx,user_ind_columns col where col.index_name=idx.index_name" +
        " and idx.table_owner=':schema'" +
        " order by idx.table_name,col.column_position")
  }

  override def supportsCommentOn = true
}
