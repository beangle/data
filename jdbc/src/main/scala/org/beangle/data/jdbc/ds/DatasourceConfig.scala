/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.jdbc.ds

import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.Strings.{isEmpty, isNotEmpty, substringBetween}
import org.beangle.commons.lang.reflect.Reflections
import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.data.jdbc.meta.Identifier
import org.beangle.data.jdbc.vendor.{DriverInfo, Vendors}

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
      else Reflections.newInstance[Dialect]((xml \\ "dialect").text.trim)

    val dbconf = new DatasourceConfig(driverName, dialect)
    if (isNotEmpty(url)) dbconf.props.put("url", url)

    if ((xml \ "@name").nonEmpty) dbconf.name = (xml \ "@name").text.trim
    dbconf.user = (xml \\ "user").text.trim
    dbconf.password = (xml \\ "password").text.trim
    dbconf.catalog = dialect.engine.toIdentifier((xml \\ "catalog").text.trim)

    var schemaName = (xml \\ "schema").text.trim
    if (isEmpty(schemaName)) {
      schemaName = dialect.defaultSchema
      if (schemaName == "$user") schemaName = dbconf.user
    }
    dbconf.schema = dialect.engine.toIdentifier(schemaName)

    (xml \\ "props" \\ "prop").foreach { ele =>
      dbconf.props.put((ele \ "@name").text, (ele \ "@value").text)
    }

    val processed = Set("url", "driver", "props", "user", "password", "catalog", "schema")
    val dbNodeName = if ((xml \\ "datasource").isEmpty) "db" else "datasource"
    xml \\ dbNodeName \ "_" foreach { n =>
      val label = n.label
      if (!processed.contains(label) && Strings.isNotEmpty(n.text)) dbconf.props.put(label, n.text)
    }
    dbconf
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
  var schema: Identifier = _
  var catalog: Identifier = _

  def this(data: collection.Map[String, String]) {
    this(data("driver"), Vendors.drivers(data("driver")).vendor.dialect)
    data.foreach {
      case (k, v) =>
        k match {
          case "user"     => this.user = v
          case "password" => this.password = v
          case "schema"   => this.schema = Identifier(v)
          case "catalog"  => this.catalog = Identifier(v)
          case "name"     => this.name = v
          case "driver"   =>
          case _          => props.put(k, v)
        }
    }
  }
}
