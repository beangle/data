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

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URL

import javax.sql.DataSource
import org.beangle.commons.bean.{Disposable, Factory, Initializing}
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.net.http.HttpUtils

/**
 * Build a DataSource from file: or http: config url
 * @author chaostone
 */
class DataSourceFactory extends Factory[DataSource] with Initializing with Disposable {
  var url: String = _
  var user: String = _
  var password: String = _
  var driver: String = _
  var name: String = _
  var props: collection.mutable.Map[String, String] = Collections.newMap

  private var _result: DataSource = _

  override def result: DataSource = {
    _result
  }

  override def destroy(): Unit = {
    DataSourceUtils.close(result)
  }

  override def init(): Unit = {
    val isXML = url.endsWith(".xml")
    if (null != url) {
      if (url.startsWith("jdbc:")) {
        if (null == driver) {
          driver = Strings.substringBetween(url, "jdbc:", ":")
          props.put("url", url)
        }
      } else if (url.startsWith("http")) {
        assert(isXML, "url should ends with xml")
        val text = HttpUtils.getText(url).getOrElse("")
        val is = new ByteArrayInputStream(text.getBytes)
        merge(readConf(is))
      } else {
        assert(isXML, "url should ends with xml")
        val f = new java.io.File(url)
        val urlAddr = if (f.exists) f.toURI.toURL else new URL(url)
        merge(readConf(urlAddr.openStream()))
      }
    }
    postInit()
    _result = DataSourceUtils.build(driver, user, password, props)
  }

  protected def postInit(): Unit = {

  }

  private def readConf(is: InputStream): DatasourceConfig = {
    var conf: DatasourceConfig = null
    conf = DataSourceUtils.parseXml(is, this.name)
    conf
  }

  private def merge(conf: DatasourceConfig): Unit = {
    if (null == user) user = conf.user
    if (null == password) password = conf.password
    if (null == driver) driver = conf.driver
    if (null == name) name = conf.name
    conf.props foreach { e =>
      if (!props.contains(e._1)) props.put(e._1, e._2)
    }
  }
}
