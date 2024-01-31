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

import java.sql.Types
import java.sql.Types.*
import scala.collection.mutable

class Oracle10g extends AbstractEngine {
  registerReserved("oracle.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar2($l)", LONGVARCHAR -> "long",
    NCHAR -> "nchar($l)", NVARCHAR -> "nvarchar2($l)", LONGNVARCHAR -> "nvarchar2($l)",
    BOOLEAN -> "number(1,0)", BIT -> "number(1,0)",
    SMALLINT -> "number(5,0)", TINYINT -> "number(3,0)", INTEGER -> "number(10,0)", BIGINT -> "number(19,0)",
    REAL -> "real", FLOAT -> "float", DOUBLE -> "double precision",
    DECIMAL -> "number($p,$s)", NUMERIC -> "number($p,$s)",
    DATE -> "date", TIME -> "date", TIMESTAMP -> "date", TIMESTAMP_WITH_TIMEZONE -> "timestamp with time zone",
    BINARY -> "raw", VARBINARY -> "long raw", LONGVARBINARY -> "long raw",
    BLOB -> "blob", CLOB -> "clob", NCLOB -> "nclob",
    JAVA_OBJECT -> "blob")

  registerTypes2(
    (VARCHAR, 4000, "varchar2($l)"), (VARCHAR, 2 * 1024 * 1024 * 1024, "clob"), (NUMERIC, 38, "number($p,$s)"),
    (NUMERIC, Int.MaxValue, "number(38,$s)"), (VARBINARY, 2000, "raw($l)"))

  options.sequence { s =>
    s.createSql = "create sequence {name} increment by {increment} start with {start} cache {cache} {cycle}"
    s.nextValSql = "select {name}.nextval from dual"
    s.selectNextValSql = "{name}.nextval"
  }

  options.table.drop.sql = "drop table {name} cascade constraints"

  options.table.alter { a =>
    a.addColumn = "add {column} {type}"
    a.changeType = "modify {column} {type}"
    a.setDefault = "modify {column} default {value}"
    a.dropDefault = "modify {column} default null"
    a.setNotNull = "modify {column} not null"
    a.dropNotNull = "modify {column} null"
    a.dropColumn = "drop column {column}"
    a.renameColumn = "rename column {oldcolumn} to {newcolumn}"

    a.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.dropConstraint = "drop constraint {name}"
  }

  options.comment.supportsCommentOn = true

  options.validate()

  override def maxIdentifierLength: Int = 30

  metadataLoadSql.sequenceSql = "select sequence_name,last_number as next_value,increment_by,cache_size,cycle_flag " +
    "from all_sequences where sequence_owner=':schema'"

  metadataLoadSql.primaryKeySql =
    "select con.constraint_name PK_NAME,con.owner TABLE_SCHEM,con.table_name TABLE_NAME,col.column_name COLUMN_NAME" +
      " from all_constraints con,all_cons_columns col" +
      " where con.constraint_type='P' and con.constraint_name=col.constraint_name and col.owner=con.owner" +
      " and con.owner=':schema'"

  metadataLoadSql.importedKeySql =
    "select mycon.constraint_name FK_NAME,mycon.owner FKTABLE_SCHEM,mycon.table_name FKTABLE_NAME,mycol.column_name FKCOLUMN_NAME," +
      " case when mycon.delete_rule='CASCADE' then 0 " +
      "  when mycon.delete_rule='RESTRICT' then 1 " +
      "  when mycon.delete_rule='SET NULL' then 2 " +
      "  when mycon.delete_rule='NO ACTION' then 3 " +
      "  else 4  end " +
      " DELETE_RULE,rcon.owner PKTABLE_SCHEM,rcon.table_name PKTABLE_NAME,rcol.column_name PKCOLUMN_NAME " +
      " from all_constraints mycon,all_cons_columns mycol,all_constraints rcon,all_cons_columns rcol " +
      " where mycon.constraint_type='R' " +
      " and mycon.constraint_name=mycol.constraint_name and rcon.constraint_name=rcol.constraint_name " +
      " and mycol.owner=mycon.owner and rcon.owner=rcol.owner" +
      " and mycon.r_constraint_name=rcon.constraint_name " +
      " and mycon.owner=':schema' "

  metadataLoadSql.indexInfoSql =
    "select idx.INDEX_NAME,idx.table_owner TABLE_SCHEM,idx.table_name TABLE_NAME, case when uniqueness ='UNIQUE' then 0 else 1 end as NON_UNIQUE," +
      " col.COLUMN_NAME,case when col.descend ='ASC' then  'A' else  'D' end as ASC_OR_DESC,col.column_position ORDINAL_POSITION" +
      " from all_indexes idx,all_ind_columns col where col.index_name=idx.index_name" +
      " and idx.table_owner=':schema'" +
      " order by idx.table_name,col.column_position"

  metadataLoadSql.viewDefSql = "select text_vc from all_views where owner=':schema' and view_name=':view_name'"

  /** limit offset
   * FIXME distinguish sql with order by or not
   *
   * @see http://blog.csdn.net/czp11210/article/details/23958065
   */
  override def limit(querySql: String, offset: Int, limit: Int): (String, List[Int]) = {
    if (null != options.limit.pattern) return super.limit(querySql, offset, limit)
    var sql = querySql.trim()
    var isForUpdate = false
    if (sql.toLowerCase().endsWith(" for update")) {
      sql = sql.substring(0, sql.length - 11)
      isForUpdate = true
    }
    val pagingSelect = new mutable.StringBuilder(sql.length + 100)
    val hasOffset = offset > 0
    if (hasOffset) pagingSelect.append("select * from ( select row_.*, rownum _rownum_ from ( ")
    else pagingSelect.append("select * from ( ")

    pagingSelect.append(sql)
    if (hasOffset) pagingSelect.append(" ) row_ where rownum <= ?) where _rownum_ > ?")
    else pagingSelect.append(" ) where rownum <= ?")

    if (isForUpdate) pagingSelect.append(" for update")
    (pagingSelect.toString, if (hasOffset) List(limit + offset, offset) else List(limit))
  }

  override def createSchema(name: String): String = null

  override def storeCase: StoreCase = StoreCase.Upper

  override def defaultSchema: String = "$user"

  override def name: String = "Oracle"

  override def version: Version = Version("[10.1,)")

  override def resolveCode(typeCode: Int, typeName: String): Int = {
    typeCode match {
      case -101 | -102 => Types.TIMESTAMP_WITH_TIMEZONE
      case _ => typeCode
    }
  }

  override def supportBoolean: Boolean = false

  functions { f =>
    f.currentDate = "current_date"
    f.localTime = "localtimestamp"
    f.currentTime = "current_timestamp"
    f.localTimestamp = "localtimestamp"
    f.currentTimestamp = "current_timestamp"
  }

  override def systemSchemas: Seq[String] = {
    List("OUTLN", "SYS", "SYSTEM", "SI_INFORMTN_SCHEMA", "DIP", "ANONYMOUS", "AUDSYS",
      "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "GSMADMIN_INTERNAL", "GSMCATUSER",
      "GSMUSER", "ORACLE_OCM", "MDDATA", "DBSFWUSER", "DBSNMP", "DVF", "ORDDATA", "ORDPLUGINS",
      "REMOTE_SCHEDULER_AGENT", "XDB","BACKMAN")
  }

}

class Oracle12c extends Oracle10g {
  options.limit { l =>
    l.pattern = "{} fetch first ? rows only"
    l.offsetPattern = "{} offset ? rows fetch next ? rows only"
    l.bindInReverseOrder = false
  }

  override def maxIdentifierLength: Int = 128

  override def version: Version = Version("[12.2,)")
}
