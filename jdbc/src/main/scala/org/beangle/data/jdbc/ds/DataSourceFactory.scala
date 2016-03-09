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
package org.beangle.data.jdbc.ds

import java.io.{ BufferedReader, ByteArrayInputStream, FileInputStream, InputStreamReader }
import java.net.{ URLConnection, URL }
import org.beangle.commons.bean.{ Factory, Initializing }
import org.beangle.commons.io.IOs
import org.beangle.commons.lang.Strings
import javax.script.ScriptEngineManager
import javax.sql.DataSource
import java.io.InputStream
import java.net.HttpURLConnection
import org.beangle.commons.bean.Disposable
import org.beangle.commons.collection.Collections

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
  var props = Collections.newMap[String, String]

  private var _result: DataSource = null

  override def result: DataSource = {
    _result
  }

  override def destroy(): Unit = {
    DataSourceUtils.close(result)
  }

  override def init(): Unit = {
    if (null != url) {
      val isXML = url.endsWith(".xml")
      if (url.startsWith("jdbc:")) {
        if (null == driver) {
          driver = Strings.substringBetween(url, "jdbc:", ":")
          props.put("url", url)
        }
      } else if (url.startsWith("file:")) {
        merge(readConf(new FileInputStream(url.substring(5)), isXML))
      } else if (!url.contains(':')) {
        merge(readConf(new FileInputStream(url), isXML))
      } else {
        val text = getURLText(url)
        val is = new ByteArrayInputStream(text.getBytes)
        merge(readConf(is, isXML))
      }
    }
    postInit()
    _result = DataSourceUtils.build(driver, user, password, props)
  }

  protected def postInit(): Unit = {

  }

  private def readConf(is: InputStream, isXML: Boolean): DatasourceConfig = {
    var conf: DatasourceConfig = null
    if (isXML) {
      (scala.xml.XML.load(is) \\ "datasource") foreach { elem =>
        val one = DatasourceConfig.build(elem)
        if (name != null) {
          if (name == one.name) conf = one
        } else {
          conf = one
        }
      }
    } else {
      conf = new DatasourceConfig(DataSourceUtils.parseJson(IOs.readString(is)))
    }
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

  private def getURLText(url: String): String = {
    var conn: URLConnection = null
    try {
      conn = new URL(url).openConnection()
      val in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))
      var line = in.readLine()
      val stringBuffer = new StringBuffer(255)
      while (line != null) {
        stringBuffer.append(line)
        stringBuffer.append("\n")
        line = in.readLine()
      }
      stringBuffer.toString()
    } catch {
      case e: Throwable => throw new RuntimeException(e)
    } finally {
      if (conn != null) {
        conn match {
          case hcon: HttpURLConnection => hcon.disconnect()
          case _                       =>
        }
      }
    }
  }

}
