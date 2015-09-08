/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2015, Beangle Software.
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
package org.beangle.data.jdbc.ds

import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.lang.Strings.{ isEmpty, isNotEmpty, substringBetween }
import org.beangle.data.jdbc.dialect.{ Dialect, Name }
import org.beangle.data.jdbc.vendor.{ DriverInfo, Vendors }

object DatasourceConfig {

  def build(xml: scala.xml.Node): DatasourceConfig = {
    var driver: DriverInfo = null
    val url = (xml \\ "url").text.trim
    var driverName = (xml \\ "driver").text.trim
    if (isEmpty(driverName) && isNotEmpty(url)) driverName = substringBetween(url, "jdbc:", ":")

    Vendors.drivers.get(driverName) match {
      case Some(d) => driver = d
      case None    => throw new RuntimeException("Not Supported:[" + driverName + "] supports:" + Vendors.driverPrefixes)
    }
    val dialect =
      if ((xml \ "@dialect").isEmpty) driver.vendor.dialect
      else ClassLoaders.load((xml \\ "dialect").text.trim).newInstance().asInstanceOf[Dialect]

    val dbconf = new DatasourceConfig(driverName, dialect)

    if (!(xml \ "@name").isEmpty) dbconf.name = (xml \ "@name").text.trim
    dbconf.user = (xml \\ "user").text.trim
    dbconf.password = (xml \\ "password").text.trim
    dbconf.catalog = dialect.parse((xml \\ "catalog").text.trim)

    addProperty(dbconf, xml, "serverName", "databaseName", "portNumber", "url")
    (xml \\ "db" \\ "props" \\ "prop").foreach { ele =>
      dbconf.props.put((ele \ "@name").text, (ele \ "@value").text);
    }
    var schemaName = (xml \\ "schema").text.trim
    if (isEmpty(schemaName)) {
      schemaName = dialect.defaultSchema
      if (schemaName == "$user") schemaName = dbconf.user
    }
    dbconf.schema = dialect.parse(schemaName)
    dbconf
  }

  private def addProperty(dbconf: DatasourceConfig, xml: scala.xml.Node, attrs: String*): Unit = {
    attrs foreach { attr =>
      if (!(xml \\ attr).isEmpty) dbconf.props.put(attr, (xml \\ attr).text.trim)
    }
  }

}

/**
 * using serverName/database or url alternative
 */
class DatasourceConfig(val driver: String, val dialect: Dialect) {
  var name: String = _

  var user: String = _
  var password: String = _

  var props = new collection.mutable.HashMap[String, String]
  var schema: Name = _
  var catalog: Name = _

  def this(data: collection.Map[String, String]) {
    this(data("driver"), Vendors.drivers(data("driver")).vendor.dialect)
    data.foreach {
      case (k, v) =>
        k match {
          case "user"     => this.user = v
          case "password" => this.password = v
          case "schema"   => this.schema = new Name(v)
          case "catalog"  => this.catalog = new Name(v)
          case "name"     => this.name = v
          case _          => props.put(k, v)
        }
    }
  }
}
