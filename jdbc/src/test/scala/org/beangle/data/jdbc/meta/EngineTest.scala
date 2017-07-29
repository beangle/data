package org.beangle.data.jdbc.meta

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.dialect.{ DB2Dialect, H2Dialect, MySQLDialect, OracleDialect, PostgreSQLDialect, SQLServerDialect }

object EngineTest {

  def main(args: Array[String]): Unit = {
    printTypeMatrix()
  }

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
      new SQLServerDialect, new DB2Dialect)

    printPad("Type/DBEngine")
    for (dialect <- dialects) {
      printPad(Strings.replace(dialect.getClass.getSimpleName, "Dialect", ""))
    }

    println()
    for (i <- 0 until types.length) {
      printPad(typeNames(i))
      for (dialect <- dialects) {
        val typeName =
          try {
            dialect.engine.typeNames.get(types(i))
          } catch {
            case e: Exception => "error"
          }
        printPad(typeName)
      }
      println("")
    }
  }

}