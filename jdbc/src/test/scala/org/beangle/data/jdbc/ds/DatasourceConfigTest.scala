package org.beangle.data.jdbc.ds

import org.junit.runner.RunWith
import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import scala.xml.XML
import org.beangle.commons.lang.ClassLoaders
import org.beangle.commons.collection.Properties

/**
 * @author chaostone
 */
@RunWith(classOf[JUnitRunner])
class DatasourceConfigTest extends FlatSpec with Matchers {
  "DatasourceConfig " should "build a correct orace datasource" in {
    (XML.load(ClassLoaders.getResource("datasources.xml")) \ "datasource") foreach { ds =>
      val config = DatasourceConfig.build(ds)
      if (config.name == "tigre") assert(config.props.contains("driverType"))
    }
  }

  "DatasourceConfig " should "build a url json" in {
    val urlJson = """{"driver":"postgresql","url":"jdbc:postgresql://localhost:5432/platform","user":"test","maximumPoolSize":10,"password":"0420b13b9aa5256f73bcf9a670acd356"}"""
    val dsc = new DatasourceConfig(DataSourceUtils.parseJson(urlJson).asInstanceOf[collection.Map[String, String]])
    assert(!dsc.props.contains("driver"))

    val serverJson = """{"driver":"postgresql","serverName":"localhost","user":"test","databaseName":"postgres","password":"0420b13b9aa5256f73bcf9a670acd356","portNumber":5432}"""
    val dsc2 = new DatasourceConfig(DataSourceUtils.parseJson(urlJson).asInstanceOf[collection.Map[String, String]])
    assert(!dsc2.props.contains("driver"))

  }

}
