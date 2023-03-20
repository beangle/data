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
import org.beangle.data.jdbc.meta.{Index, PrimaryKey, Table}

import java.sql.Types.*

class MySQL5 extends AbstractEngine {
  registerReserved("mysql.txt")

  registerTypes(
    CHAR -> "char($l)", VARCHAR -> "longtext", LONGVARCHAR -> "longtext",
    NCHAR -> "nchar($l)", NVARCHAR -> "nvarchar($l)", LONGNVARCHAR -> "nvarchar($l)",
    BOOLEAN -> "bit", BIT -> "bit",
    TINYINT -> "tinyint", SMALLINT -> "smallint", INTEGER -> "integer", BIGINT -> "bigint",
    REAL -> "real", FLOAT -> "float", DOUBLE -> "double precision",
    DECIMAL -> "decimal($p,$s)", NUMERIC -> "decimal($p,$s)",
    DATE -> "date", TIME -> "time", TIMESTAMP -> "datetime", TIMESTAMP_WITH_TIMEZONE -> "timestamp",
    BINARY -> "binary($l)", VARBINARY -> "longblob", LONGVARBINARY -> "longblob",
    BLOB -> "longblob", CLOB -> "longtext", NCLOB -> "longtext",
    JAVA_OBJECT -> "json")

  registerTypes2(
    (VARCHAR, 500, "varchar($l)"),
    (VARCHAR, 65535, "text"),
    (VARCHAR, 16777215, "mediumtext"),

    (NUMERIC, 65, "decimal($p, $s)"),
    (NUMERIC, Int.MaxValue, "decimal(65, $s)"),

    (BINARY, 255, "binary($l)"),
    (BINARY, 65535, "blob"),
    (BINARY, Int.MaxValue, "longblob"),

    (VARBINARY, 255, "tinyblob"),
    (VARBINARY, 65535, "blob"),
    (VARBINARY, 16777215, "mediumblob"),
    (LONGVARBINARY, 16777215, "mediumblob"))

  options.sequence.supports = false
  options.table.alter { a =>
    a.addColumn = "add {column} {type}"
    a.changeType = "modify column {column} {type}"
    a.setDefault = "alter {column} set default {value}"
    a.dropDefault = "alter {column} drop default"
    a.setNotNull = "modify {column} {type} not null"
    a.dropNotNull = "modify {column} {type}"
    a.dropColumn = "drop column {column}"
    a.renameColumn = "change column {oldcolumn} {newcolumn} {type}"

    a.addPrimaryKey = "add primary key ({column-list})"
    a.dropConstraint = "drop constraint {name}"
  }

  options.limit.pattern = "{} limit ?"
  options.limit.offsetPattern = "{} limit ? offset ?"
  options.limit.bindInReverseOrder = true

  options.comment.supportsCommentOn = false

  override def maxIdentifierLength: Int = 64

  override def catelogAsSchema: Boolean = true

  override def createSchema(name: String): String = s"create database if not exists ${name} DEFAULT CHARSET utf8 COLLATE utf8_general_ci"

  override def foreignKeySql(constraintName: String, foreignKey: Iterable[String],
                             referencedTable: String, primaryKey: Iterable[String]): String = {
    val cols = Strings.join(foreignKey, ", ")
    new StringBuffer(30).append(" add index ").append(constraintName).append(" (").append(cols)
      .append("), add constraint ").append(constraintName).append(" foreign key (").append(cols)
      .append(") references ").append(referencedTable).append(" (")
      .append(Strings.join(primaryKey, ", ")).append(')').toString
  }

  override def alterTableDropPrimaryKey(table: Table, pk: PrimaryKey): String = {
    s"alter table ${table.qualifiedName}  drop primary key"
  }

  override def dropIndex(i: Index): String = {
    "drop index " + i.literalName + " on " + i.table.qualifiedName
  }

  override def defaultSchema: String = "PUBLIC"

  override def name: String = "MySQL"

  override def quoteChars: (Char, Char) = ('`', '`')

  override def version: Version = Version("[5.5,)")
}
