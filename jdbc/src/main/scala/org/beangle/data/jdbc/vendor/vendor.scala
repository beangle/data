/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
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
package org.beangle.data.jdbc.vendor

import org.beangle.data.jdbc.dialect.{ DB2Dialect, DerbyDialect, Dialect, H2Dialect, HSQL2Dialect, MySQLDialect, OracleDialect, PostgreSQLDialect, SQLServer2008Dialect }

object Vendors {

  /**
   * PostgreSQL driver info
   * @see https://jdbc.postgresql.org/documentation/head/connect.html
   * @see http://impossibl.github.io/pgjdbc-ng/
   */
  val postgresql = Vendor("PostgreSQL", new PostgreSQLDialect,
    Driver("postgresql", "org.postgresql.ds.PGSimpleDataSource", "org.postgresql.Driver", "//<host>:<port>/<database_name>"),
    Driver("pgsql", "com.impossibl.postgres.jdbc.PGDataSource", "com.impossibl.postgres.jdbc.PGDriver", "//<host>:<port>/<database_name>"))

  val oracle = Vendor("Oracle", new OracleDialect,
    Driver("oracle", "oracle.jdbc.pool.OracleDataSource", "oracle.jdbc.driver.OracleDriver", "thin:@//<host>:<port>/<service_name>",
      "thin:@<host>:<port>:<SID>", "thin:@(DESCRIPTION=(ADDRESS_LIST=(LOAD_BALANCE=OFF)(FAILOVER=ON)"
        + "(ADDRESS=(PROTOCOL=TCP)(HOST=<host1>)(PORT=<port1>))"
        + "(ADDRESS=(PROTOCOL=TCP)(HOST=<host2>)(PORT=<port2>)))"
        + "(CONNECT_DATA=SERVICE_NAME=<service_name>)(SERVER=DEDICATED)))"))

  val mysql = Vendor("MySQL", new MySQLDialect, Driver("mysql", "com.mysql.jdbc.jdbc2.optional.MysqlDataSource", "com.mysql.jdbc.Driver", "//<host>:<port>/<database_name>"))

  val mariadb = Vendor("MariaDB", new MySQLDialect, Driver("mariadb", "org.mariadb.jdbc.MySQLDataSource", "org.mariadb.jdbc.Driver", "//<host>:<port>/<database_name>"))

  val h2 = Vendor("H2", new H2Dialect, Driver("h2", "org.h2.jdbcx.JdbcDataSource", "org.h2.Driver", "[<path>]<database_name>", "mem:<database_name>",
    "tcp://<server>[:<port>]/[<path>]<database_name>"))

  val db2 = Vendor("DB2", new DB2Dialect, Driver("db2", "com.ibm.db2.jcc.DB2SimpleDataSource", "com.ibm.db2.jcc.DB2Driver", "//<host>[:<port>]/<database_name>",
    "<database_name>"))

  val derby = Vendor("Apache Derby", new DerbyDialect, Driver("derby", "org.apache.derby.jdbc.ClientDataSource", "org.apache.derby.jdbc.ClientDriver",
    "[<subsubprotocol>:][<database_name>][;<attribute>=<value>]*"))

  val hsql = Vendor("HSQL Database Engine", new HSQL2Dialect, Driver("hsqldb", "org.hsqldb.jdbc.JDBCDataSource", "org.hsqldb.jdbcDriver", "hsql://<host>:<port>",
    "file:<path>", "hsqls://<host>:<port>", "http://<host>:<port>", "https://<host>:<port>", "res:<database_name>"))

  //default port 1433
  val sqlserver = Vendor("Microsoft SQL Server", new SQLServer2008Dialect,
    Driver("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDataSource", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "//<server_name>:<port>"),
    Driver("jtds", "net.sourceforge.jtds.jdbcx.JtdsDataSource", "net.sourceforge.jtds.jdbc.Driver", "sqlserver://<server_name>:<port>/<database_name>"))

  val list = List(postgresql, oracle, mysql, mariadb, h2, db2, derby, hsql, sqlserver)

  def drivers: Map[String, DriverInfo] = {
    val drivers = new collection.mutable.HashMap[String, DriverInfo]
    for (v <- Vendors.list; d <- v.drivers)
      drivers += (d.prefix -> d)
    drivers.toMap
  }

  def driverPrefixes: List[String] = for (v <- Vendors.list; d <- v.drivers) yield d.prefix
}

object Vendor {
  def apply(name: String, dialect: Dialect, drivers: DriverInfo*): VendorInfo = {
    new VendorInfo(name, dialect, drivers)
  }
}

/**
 * vendor default dialect
 */
class VendorInfo(val name: String, val dialect: Dialect, val drivers: Seq[DriverInfo]) {
  drivers foreach (d => d.vendor = this)
}