package org.beangle.data.jdbc.vendor

object Vendors {

  val oracle = Vendor("Oracle",
    Driver("oracle.jdbc.driver.OracleDriver", "oracle", "thin:@//<host>:<port>/<service_name>",
      "thin:@<host>:<port>:<SID>", "thin:@(DESCRIPTION=(ADDRESS_LIST=(LOAD_BALANCE=OFF)(FAILOVER=ON)"
        + "(ADDRESS=(PROTOCOL=TCP)(HOST=<host1>)(PORT=<port1>))"
        + "(ADDRESS=(PROTOCOL=TCP)(HOST=<host2>)(PORT=<port2>)))"
        + "(CONNECT_DATA=SERVICE_NAME=<service_name>)(SERVER=DEDICATED)))"))

  val mysql = Vendor("MySQL", Driver("com.mysql.jdbc.Driver", "mysql", "//<host>:<port>/<database_name>"))

  val postgresql = Vendor("PostgreSQL", Driver("org.postgresql.Driver", "postgresql", "//<host>:<port>/<database_name>"))

  val h2 = Vendor("H2", Driver("org.h2.Driver", "h2", "[<path>]<database_name>", "mem:<database_name>",
    "tcp://<server>[:<port>]/[<path>]<database_name>"))

  val db2 = Vendor("DB2", Driver("com.ibm.db2.jcc.DB2Driver", "db2", "//<host>[:<port>]/<database_name>", "<database_name>"))

  val derby = Vendor("Apache Derby", Driver("", "derby", "[subsubprotocol:][database_name][;attribute=value]*"))

  val hsql = Vendor("HSQL Database Engine", Driver("org.hsqldb.jdbcDriver", "hsqldb", "hsql://<host>:<port>", "file:<path>",
    "hsqls://<host>:<port>", "http://<host>:<port>", "https://<host>:<port>", "res:<database_name>"))

  val sqlserver = Vendor("Microsoft SQL Server", Driver("com.microsoft.sqlserver.jdbc.SQLServerDriver", "sqlserver", "//<server_name>:<port>"),
    Driver("net.sourceforge.jtds.jdbc.Driver", "jtds", "sqlserver://<server_name>:<port>/<database_name>"))

  val list = List(oracle, mysql, postgresql, h2, db2, derby, hsql, sqlserver)

  def drivers: Map[String, DriverInfo] = {
    val drivers = new collection.mutable.HashMap[String, DriverInfo]
    for (v <- Vendors.list; d <- v.drivers)
      drivers += (d.prefix -> d)
    drivers.toMap
  }

  def driverPrefixes: List[String] = for (v <- Vendors.list; d <- v.drivers) yield d.prefix
}


object Vendor {
  def apply(name: String, drivers: DriverInfo*): VendorInfo = {
    new VendorInfo(name, drivers)
  }
}

class VendorInfo(val name: String, val drivers: Seq[DriverInfo]) {

}