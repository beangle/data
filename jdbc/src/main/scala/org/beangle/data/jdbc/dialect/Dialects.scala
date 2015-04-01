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
package org.beangle.data.jdbc.dialect

import scala.collection.mutable

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.vendor.VendorInfo
import org.beangle.data.jdbc.vendor.Vendors

object Dialects {

  val registeredDialects = new mutable.HashMap[VendorInfo, List[Dialect]]

  def getDialect(vendor: VendorInfo, version: String): Option[Dialect] = {
    for (dialects <- registeredDialects.get(vendor))
      return dialects.find(d => d.support(version))
    None
  }

  def register(product: VendorInfo, dialects: Dialect*) {
    registeredDialects.put(product, dialects.toList)
  }

  register(Vendors.oracle, new OracleDialect)
  register(Vendors.db2, new DB2Dialect)
  register(Vendors.derby, new DerbyDialect)
  register(Vendors.h2, new H2Dialect)
  register(Vendors.hsql, new HSQL2Dialect)
  register(Vendors.mysql, new MySQLDialect)
  register(Vendors.postgresql, new PostgreSQLDialect)
  register(Vendors.sqlserver, new SQLServer2008Dialect, new SQLServer2005Dialect, new SQLServerDialect)

  private def printPad(name: String) { print(Strings.rightPad(name, 17, ' ')) }

  def printTypeMatrix() {
    import java.sql.Types._
    val types = Array(BOOLEAN, BIT, CHAR, INTEGER, SMALLINT, TINYINT, BIGINT,
      FLOAT, DOUBLE, DECIMAL, NUMERIC, DATE, TIME, TIMESTAMP, VARCHAR, LONGVARCHAR,
      BINARY, VARBINARY, LONGVARBINARY, BLOB, CLOB)

    val typeNames = Array("BOOLEAN", "BIT", "CHAR", "INTEGER", "SMALLINT", "TINYINT", "BIGINT",
      "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC", "DATE", "TIME", "TIMESTAMP", "VARCHAR", "LONGVARCHAR",
      "BINARY", "VARBINARY", "LONGVARBINARY", "BLOB", "CLOB")

    val dialects = Array(new OracleDialect, new H2Dialect, new MySQLDialect, new PostgreSQLDialect,
      new SQLServer2005Dialect, new DB2Dialect)

    printPad("Type/Dialect")
    for (dialect <- dialects) {
      printPad(Strings.replace(dialect.getClass.getSimpleName, "Dialect", ""))
    }

    println()
    for (i <- 0 until types.length) {
      printPad(typeNames(i))
      for (dialect <- dialects) {
        val typeName =
          try {
            dialect.typeNames.get(types(i))
          } catch {
            case e: Exception => "error"
          }
        printPad(typeName)
      }
      println("")
    }
  }
}

abstract class Dialect {

  def tableGrammar: TableGrammar

  def limitGrammar: LimitGrammar

  def sequenceGrammar: SequenceGrammar

  def defaultSchema: String

  def typeNames: TypeNames

  def translate(typeCode: Int, size: Int, scale: Int):Tuple2[Int,String]

  def keywords: Set[String]

  def supportsCascadeDelete: Boolean

  def isCaseSensitive: Boolean

  def getAddForeignKeyConstraintString(constraintName: String, foreignKey: Array[String],
    referencedTable: String, primaryKey: Array[String], referencesPrimaryKey: Boolean): String

  def metadataGrammar: MetadataGrammar

  def support(version: String): Boolean
}
