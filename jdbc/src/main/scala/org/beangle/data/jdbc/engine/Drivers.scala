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

import org.beangle.commons.lang.Strings.replace
import org.beangle.data.jdbc.engine.Drivers.drivers

import java.util.regex.Pattern

object Drivers {

  /** PostgreSQL driver info
   *
   * @see https://jdbc.postgresql.org/documentation/head/connect.html
   * @see http://impossibl.github.io/pgjdbc-ng/
   */
  val drivers = List(
    driver("postgresql", "org.postgresql.ds.PGSimpleDataSource",
      "org.postgresql.Driver", "//<host>:<port>/<database_name>"),
    driver("pgsql", "com.impossibl.postgres.jdbc.PGDataSource",
      "com.impossibl.postgres.jdbc.PGDriver", "//<host>:<port>/<database_name>"),
    driver("oracle", "oracle.jdbc.pool.OracleDataSource",
      "oracle.jdbc.OracleDriver", "thin:@//<host>:<port>/<service_name>",
      "thin:@<host>:<port>:<SID>", "thin:@(DESCRIPTION=(ADDRESS_LIST=(LOAD_BALANCE=OFF)(FAILOVER=ON)"
        + "(ADDRESS=(PROTOCOL=TCP)(HOST=<host1>)(PORT=<port1>))"
        + "(ADDRESS=(PROTOCOL=TCP)(HOST=<host2>)(PORT=<port2>)))"
        + "(CONNECT_DATA=SERVICE_NAME=<service_name>)(SERVER=DEDICATED)))"),
    driver("mysql", "com.mysql.cj.jdbc.MysqlDataSource",
      "com.mysql.cj.jdbc.Driver", "//<host>:<port>/<database_name>"),
    driver("mariadb", "org.mariadb.jdbc.MySQLDataSource",
      "org.mariadb.jdbc.Driver", "//<host>:<port>/<database_name>"),
    driver("h2", "org.h2.jdbcx.JdbcDataSource",
      "org.h2.Driver", "[<path>]<database_name>", "mem:<database_name>",
      "tcp://<server>[:<port>]/[<path>]<database_name>"),
    driver("db2", "com.ibm.db2.jcc.DB2SimpleDataSource",
      "com.ibm.db2.jcc.DB2Driver", "//<host>[:<port>]/<database_name>", "<database_name>"),
    driver("derby", "org.apache.derby.jdbc.ClientDataSource",
      "org.apache.derby.jdbc.ClientDriver",
      "[<subprotocol>:][<database_name>][;<attribute>=<value>]*"),
    driver("hsqldb", "org.hsqldb.jdbc.JDBCDataSource",
      "org.hsqldb.jdbcDriver", "hsql://<host>:<port>",
      "file:<path>", "hsqls://<host>:<port>", "http://<host>:<port>", "https://<host>:<port>", "res:<database_name>"),
    driver("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDataSource",
      "com.microsoft.sqlserver.jdbc.SQLServerDriver", "//<host>:<port>;databaseName=<database_name>;encrypt=false;"),
    driver("jtds", "net.sourceforge.jtds.jdbcx.JtdsDataSource",
      "net.sourceforge.jtds.jdbc.Driver", "sqlserver://<host>:<port>/<database_name>")).map(x => (x.prefix, x)).toMap

  def get(prefix: String): Option[DriverInfo] = {
    drivers.get(prefix)
  }

  private def driver(prefix: String, dataSourceClassName: String, className: String, urlformats: String*): DriverInfo = {
    DriverInfo(dataSourceClassName, className, prefix, urlformats)
  }

  def driverPrefixes: List[String] = drivers.keys.toList
}

case class DriverInfo(dataSourceClassName: String, className: String, prefix: String, urlformats: Seq[String])

class UrlFormat(val format: String) {
  val params: List[String] = findParams(format)

  private def findParams(format: String): List[String] = {
    val m = Pattern.compile("(<.*?>)").matcher(format)
    val ps = new collection.mutable.ListBuffer[String]
    while (m.find()) {
      val matched = m.group(0)
      ps += matched.substring(1, matched.length - 1)
    }
    ps.toList
  }

  def fill(values: Map[String, String]): String = {
    var result = format
    for ((k, v) <- values) result = replace(result, "<" + k + ">", v)
    result = replace(result, "[", "")
    result = replace(result, "]", "")
    result
  }
}
