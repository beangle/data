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

import java.sql.Types.*

class H2 extends AbstractEngine {
  registerReserved("h2.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "varchar($l)", LONGVARCHAR -> "character varying",
    NCHAR -> "nchar($l)", NVARCHAR -> "nchar varying($l)", LONGNVARCHAR -> "nchar varying",
    BOOLEAN -> "boolean", BIT -> "boolean",
    TINYINT -> "tinyint", SMALLINT -> "smallint", INTEGER -> "integer", BIGINT -> "bigint",
    REAL -> "real", FLOAT -> "float", DOUBLE -> "double",
    DECIMAL -> "decimal", NUMERIC -> "numeric($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "timestamp", TIMESTAMP_WITH_TIMEZONE -> "timestamp with time zone",
    BINARY -> "binary($l)", VARBINARY -> "varbinary($l)", LONGVARBINARY -> "longvarbinary",
    BLOB -> "blob", CLOB -> "clob", NCLOB -> "nclob",
    JAVA_OBJECT -> "json")

  registerTypes2(
    (VARCHAR, 1_000_000_00, "varchar($l)"), (VARCHAR, Int.MaxValue, "character large object"),
    (VARBINARY, 1_000_000_00, "varbinary($l)"), (VARBINARY, Int.MaxValue, "binary large object"),
    (BINARY, 1_000_000_00, "binary($l)"), (BINARY, Int.MaxValue, "binary large object"))

  metadataLoadSql.sequenceSql = "select sequence_name,base_value,increment,cache from information_schema.sequences where sequence_schema=':schema'"

  options.sequence { s =>
    s.nextValSql = "call next value for {name}"
    s.selectNextValSql = "next value for {name}"
    s.createSql = "create sequence {name} start with {start} increment by {increment} cache {cache}"
    s.dropSql = "drop sequence if exists {name}"
  }

  options.table.drop.sql = "drop table {name} cascade"

  options.limit.pattern = "{} limit ?"
  options.limit.offsetPattern = "{} limit ? offset ?"
  options.limit.bindInReverseOrder = true
  options.comment.supportsCommentOn = true

  options.table.alter { a =>
    a.changeType = "alter {column} {type}"
    a.setDefault = "alter {column} set default {value}"
    a.dropDefault = "alter {column} set default null"
    a.setNotNull = "alter {column} set not null"
    a.dropNotNull = "alter {column} set null"
    a.addColumn = "add {column} {type}"
    a.dropColumn = "drop column {column}"
    a.renameColumn = "alter column {oldcolumn} rename to {newcolumn}"

    a.addPrimaryKey = "add constraint {name} primary key ({column-list})"
    a.dropConstraint = "drop constraint if exists {name} cascade"
  }

  options.validate()

  functions { f =>
    f.currentDate = "current_date"
    f.localTime = "current_time"
    f.currentTime = "current_time"
    f.localTimestamp = "current_timestamp"
    f.currentTimestamp = "current_timestamp"
  }

  override def storeCase: StoreCase = StoreCase.Upper

  override def defaultSchema: String = "PUBLIC"

  override def name: String = "H2"

  override def version: Version = Version("[2.1,)")
}
