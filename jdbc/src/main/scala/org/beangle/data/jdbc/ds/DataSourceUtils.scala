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

package org.beangle.data.jdbc.ds

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.Strings.{isEmpty, isNotEmpty, substringBetween}
import org.beangle.commons.lang.reflect.{BeanInfos, Reflections}
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.engine.{DriverInfo, Drivers, Engine}
import org.beangle.data.jdbc.meta.Identifier

import java.io.InputStream
import java.sql.Connection
import java.util.Properties
import javax.sql.DataSource
import scala.language.existentials

object DataSourceUtils extends Logging {

  def build(driver: String, username: String, password: String, props: collection.Map[String, String]): DataSource = {
    new HikariDataSource(new HikariConfig(buildProperties(driver, username, password, props)))
  }

  def close(dataSource: DataSource): Unit = {
    dataSource match {
      case hikarids: HikariDataSource => hikarids.close()
      case _ =>
        val method = dataSource.getClass.getMethod("close")
        if (null != method) {
          method.invoke(dataSource)
        } else {
          logger.info(s"Cannot find ${dataSource.getClass.getName}'s close method")
        }
    }
  }

  private def buildProperties(driver: String, username: String, password: String, props: collection.Map[String, String]): Properties = {
    val properties = new Properties
    val writables = BeanInfos.get(classOf[HikariConfig]).writables.keySet

    props.foreach { e =>
      var key = if (e._1 == "url") "jdbcUrl" else e._1
      if (!writables.contains(key)) key = "dataSource." + key
      properties.put(key, e._2)
    }

    if (driver == "oracle" && !properties.containsKey("jdbcUrl") && !props.contains("driverType")) properties.put("dataSource.driverType", "thin")

    if (null != username) properties.put("username", username)
    if (null != password) properties.put("password", password)

    val driverInfo = Drivers.get(driver).get
    if (properties.containsKey("jdbcUrl")) {
      Class.forName(driverInfo.className)
    } else {
      if (!properties.containsKey("dataSourceClassName")) properties.put("dataSourceClassName", driverInfo.dataSourceClassName)
    }
    properties
  }

  def parseXml(is: InputStream, name: String): DatasourceConfig = {
    var conf: DatasourceConfig = null
    (scala.xml.XML.load(is) \\ "datasource") foreach { elem =>
      val one = parseXml(elem)
      if (name != null) {
        if (name == one.name) conf = one
      } else {
        conf = one
      }
    }
    conf
  }

  def parseXml(xml: scala.xml.Node): DatasourceConfig = {
    var driver: DriverInfo = null
    val url = (xml \\ "url").text.trim
    var driverName = (xml \\ "driver").text.trim
    if (isEmpty(driverName) && isNotEmpty(url)) driverName = substringBetween(url, "jdbc:", ":")

    Drivers.get(driverName) match {
      case Some(d) => driver = d
      case None => throw new RuntimeException("Not Supported:[" + driverName + "] supports:" + Drivers.driverPrefixes)
    }
    val dbconf = new DatasourceConfig(driverName)
    if (isNotEmpty(url)) dbconf.props.put("url", url)

    if ((xml \ "@name").nonEmpty) dbconf.name = (xml \ "@name").text.trim
    dbconf.user = (xml \\ "user").text.trim
    dbconf.password = (xml \\ "password").text.trim
    dbconf.catalog = Identifier((xml \\ "catalog").text.trim)
    dbconf.schema = Identifier((xml \\ "schema").text.trim)

    (xml \\ "props" \\ "prop").foreach { ele =>
      dbconf.props.put((ele \ "@name").text, (ele \ "@value").text)
    }

    val processed = Set("url", "driver", "props", "user", "password", "catalog", "schema")
    xml \ "_" foreach { n =>
      val label = n.label
      if (!processed.contains(label) && Strings.isNotEmpty(n.text)) dbconf.props.put(label, n.text)
    }
    dbconf
  }

  def resetConnectionAfterTransaction(con: Connection, previousIsolationLevel: Integer, resetReadOnly: Boolean): Unit = {
    try {
      if previousIsolationLevel != null then con.setTransactionIsolation(previousIsolationLevel)
      if resetReadOnly then con.setReadOnly(false)
    } catch {
      case ex: Throwable =>
    }
  }
}
