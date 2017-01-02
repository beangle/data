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

import java.sql.Types._

class PostgreSQLDialect extends AbstractDialect("[8.4)") {

  protected override def registerType() = {
    registerType(CHAR, "char($l)")
    registerType(VARCHAR, "varchar($l)")
    registerType(LONGVARCHAR, "text")

    registerType(BOOLEAN, "boolean")
    registerType(BIT, "boolean")
    registerType(BIGINT, "int8")
    registerType(SMALLINT, "int2")
    registerType(TINYINT, "int2")
    registerType(INTEGER, "int4")

    registerType(FLOAT, "float4")
    registerType(REAL, "float4")
    registerType(DOUBLE, "float8")

    registerType(DECIMAL, "numeric($p, $s)")
    registerType(DECIMAL, 1, "boolean")
    registerType(DECIMAL, 10, "integer")
    registerType(DECIMAL, 19, "bigint")
    registerType(NUMERIC, 1000, "numeric($p, $s)")
    registerType(NUMERIC, Int.MaxValue, "numeric(1000, $s)")
    registerType(NUMERIC, "numeric($p, $s)")

    registerType(DATE, "date")
    registerType(TIME, "time")
    registerType(TIMESTAMP, "timestamp")

    registerType(BINARY, "bytea")
    registerType(VARBINARY, "bytea")
    registerType(LONGVARBINARY, "bytea")

    registerType(CLOB, "text")
    registerType(BLOB, "oid")
  }

  override def sequenceGrammar = {
    val ss = new SequenceGrammar()
    ss.querySequenceSql = "select sequence_name,start_value,increment increment_by,cycle_option cycle_flag" +
      " from information_schema.sequences where sequence_schema=':schema'"
    ss.nextValSql = "select nextval (':name')"
    ss.selectNextValSql = "nextval (':name')"
    ss
  }

  override def limitGrammar = new LimitGrammarBean("{} limit ?", "{} limit ? offset ?", true)

  override def tableGrammar = {
    val bean = new TableGrammarBean()
    bean.dropSql = "drop table {} cascade"
    bean
  }

  override def defaultSchema: String = {
    "public"
  }

  override def translate(typeCode: Int, size: Int, scale: Int): Tuple2[Int, String] = {
    if (typeCode == DECIMAL) {
      size match {
        case 1  => (BOOLEAN, "boolean")
        case 5  => (SMALLINT, "int2")
        case 10 => (INTEGER, "int4")
        case 19 => (BIGINT, "int8")
        case _  => super.translate(typeCode, size, scale)
      }

    } else super.translate(typeCode, size, scale)
  }

  override def storeCase: StoreCase.Value = {
    StoreCase.Lower
  }
}
