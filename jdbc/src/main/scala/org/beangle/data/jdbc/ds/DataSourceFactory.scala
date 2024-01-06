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

import org.beangle.commons.bean.{Disposable, Factory, Initializing}
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import org.beangle.commons.net.http.HttpUtils
import org.beangle.data.jdbc.ds.DataSourceUtils.parseXml

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{URI, URL}
import javax.sql.DataSource

object DataSourceFactory {
  def build(driver: String, username: String, password: String, props: collection.Map[String, String]): DataSource = {
    val factory = new DataSourceFactory
    factory.driver = driver
    factory.user = username
    factory.password = password
    factory.props ++= props
    factory.init()
    factory.result
  }
}

/**
 * Build a DataSource from file: or http: config url
 *
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
    try {
      if (null != url) {
        if (url.startsWith("jdbc:")) {
          if (null == driver) {
            driver = Strings.substringBetween(url, "jdbc:", ":")
            props.put("url", url)
          }
        } else if (url.startsWith("http")) {
          val res = HttpUtils.getText(url)
          if res.isOk then merge(readConf(new ByteArrayInputStream(res.getText.getBytes)))
          else throw RuntimeException(s"access error :$url")
        } else {
          val f = new java.io.File(url)
          val urlAddr = if (f.exists) f.toURI.toURL else URI.create(url).toURL
          merge(readConf(urlAddr.openStream()))
        }
      }
      postInit()
      _result = DataSourceUtils.build(driver, user, password, props)
    } catch {
      case e: Throwable =>
        throw new RuntimeException(s"cannot find datasource named ${this.name} in ${this.url}", e)
    }
  }

  protected def postInit(): Unit = {
    if (password != null && password.startsWith("?")) {
      this.password =
        if (props.contains("url")) {
          DatasourceEncryptor.decrypt(user, props("url"), password.substring(1))
        } else {
          DatasourceEncryptor.decrypt(user, props("serverName"), password.substring(1))
        }
    }
  }

  private def readConf(is: InputStream): DatasourceConfig = {
    val root = scala.xml.XML.load(is)
    val datasources = root \\ "datasource"
    val dbs = root \\ "db"
    if (dbs.isEmpty && datasources.isEmpty) {
      is.close()
      DataSourceUtils.parseXml(root)
    } else {
      var conf: DatasourceConfig = null
      val nodes = if (datasources.isEmpty) dbs else datasources
      nodes foreach { elem =>
        val one = parseXml(elem)
        if (this.name != null) {
          if (this.name == one.name) conf = one
        } else {
          conf = one
        }
      }
      is.close()
      conf
    }
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
