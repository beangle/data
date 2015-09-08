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
  var props = new collection.mutable.HashMap[String, String]

  private var _result: DataSource = null

  override def result: DataSource = {
    _result
  }
  
  override def destroy(): Unit = {
    DataSourceUtils.close(result)
  }

  override def init(): Unit = {
    var conf: DatasourceConfig = null
    val isXML = url.endsWith(".xml")
    if (url.startsWith("jdbc:")) {
      if (null == driver) {
        driver = Strings.substringBetween(url, "jdbc:", ":")
        props.put("url", url)
      }
    } else if (url.startsWith("file:")) {
      merge(readConf(new FileInputStream(url.substring(5)), isXML))
    } else {
      val text = getURLText(url)
      val is = new ByteArrayInputStream(text.getBytes)
      merge(readConf(is, isXML))
    }
    _result = DataSourceUtils.build(driver, user, password, props)
  }

  private def readConf(is: InputStream, isXML: Boolean): DatasourceConfig = {
    var conf: DatasourceConfig = null
    if (isXML) (scala.xml.XML.load(is) \\ "datasource") foreach { elem =>
      val one = DatasourceConfig.build(elem)
      if (name != null) {
        if (name == one.name) conf = one
      } else {
        conf = one
      }
    }
    else conf = new DatasourceConfig(parseJson(IOs.readString(is)))
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

  private def parseJson(string: String): collection.mutable.HashMap[String, String] = {
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