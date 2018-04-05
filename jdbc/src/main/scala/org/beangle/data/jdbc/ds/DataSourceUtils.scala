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

import java.util.Properties

import scala.language.existentials

import org.beangle.commons.lang.reflect.BeanInfos
import org.beangle.commons.logging.Logging
import org.beangle.data.jdbc.vendor.Vendors

import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }

import javax.script.ScriptEngineManager
import javax.sql.DataSource

object DataSourceUtils extends Logging{

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
        }else{
          logger.info(s"Cannot find ${dataSource.getClass.getName}'s close method")
        }
    }
  }

  private def buildProperties(driver: String, username: String, password: String, props: collection.Map[String, String]): Properties = {
    val properties = new Properties
    val writables = new BeanInfos().get(classOf[HikariConfig]).getWritableProperties()

    props.foreach { e =>
      var key = if (e._1 == "url") "jdbcUrl" else e._1
      if (!writables.contains(key)) key = "dataSource." + key
      properties.put(key, e._2)
    }

    if (driver == "oracle" && !properties.containsKey("jdbcUrl") && !props.contains("driverType")) properties.put("dataSource.driverType", "thin")

    if (null != username) properties.put("username", username)
    if (null != password) properties.put("password", password)

    if (!properties.containsKey("jdbcUrl")) {
      if (!properties.containsKey("dataSourceClassName")) properties.put("dataSourceClassName", Vendors.drivers(driver).dataSourceClassName)
    } else {
      Class.forName(Vendors.drivers(driver).className)
    }
    properties
  }

  protected[ds] def parseJson(string: String): collection.mutable.HashMap[String, String] = {
    val sem = new ScriptEngineManager();
    val engine = sem.getEngineByName("javascript");
    val result = new collection.mutable.HashMap[String, String]
    val iter = engine.eval("result =" + string).asInstanceOf[java.util.Map[_, AnyRef]].entrySet().iterator();
    while (iter.hasNext()) {
      val one = iter.next()
      var value: String = null
      if (one.getValue.isInstanceOf[java.lang.Double]) {
        val d = one.getValue.asInstanceOf[java.lang.Double]
        if (java.lang.Double.compare(d, d.intValue()) > 0) value = d.toString()
        else value = String.valueOf(d.intValue())
      } else {
        value = one.getValue().toString()
      }

      val key = if (one.getKey().toString() == "maxActive") "maxTotal" else one.getKey().toString();
      result.put(key, value);
    }
    result;
  }
}
