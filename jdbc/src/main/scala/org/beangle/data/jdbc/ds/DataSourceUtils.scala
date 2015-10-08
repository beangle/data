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

import java.util.Properties
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import javax.sql.DataSource
import org.beangle.data.jdbc.vendor.Vendors
import org.beangle.commons.lang.reflect.BeanManifest

object DataSourceUtils {

  def build(driver: String, username: String, password: String, props: collection.Map[String, String]): DataSource = {
    new HikariDataSource(new HikariConfig(buildProperties(driver, username, password, props)))
  }

  def close(dataSource: DataSource): Unit = {
    dataSource match {
      case hikarids: HikariDataSource => hikarids.close()
      case _ =>
        val method = dataSource.getClass.getMethod("close")
        method.invoke(dataSource)
    }
  }

  private def buildProperties(driver: String, username: String, password: String, props: collection.Map[String, String]): Properties = {
    val properties = new Properties
    val writables = BeanManifest.load(classOf[HikariConfig]).getWritableProperties()

    props.foreach { e =>
      var key = if (e._1 == "url") "jdbcUrl" else e._1
      if (!writables.contains(key)) key = "dataSource." + key
      properties.put(key, e._2)
    }

    if (driver == "oracle" && !properties.containsKey("jdbcUrl") && !props.contains("driverType")) properties.put("dataSource.driverType", "thin")

    if (null != username) properties.put("username", username)
    if (null != password) properties.put("password", password)

    if (!properties.containsKey("jdbcUrl") && !properties.containsKey("dataSourceClassName")) {
      properties.put("dataSourceClassName", Vendors.drivers(driver).dataSourceClassName)
    }
    properties
  }
}

