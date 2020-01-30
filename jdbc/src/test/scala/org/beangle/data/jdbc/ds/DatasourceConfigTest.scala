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

import org.beangle.commons.lang.ClassLoaders
import org.junit.runner.RunWith
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.junit.JUnitRunner

import scala.xml.XML

/**
 * @author chaostone
 */
@RunWith(classOf[JUnitRunner])
class DatasourceConfigTest extends AnyFlatSpec with Matchers {
  "DatasourceConfig " should "build a correct orace datasource" in {
    (XML.load(ClassLoaders.getResource("datasources.xml").get) \ "datasource") foreach { ds =>
      val config = DataSourceUtils.parseXml(ds)
      if (config.name == "tigre") assert(config.props.contains("driverType"))
    }
  }

  "DatasourceConfig " should "build a url json" in {
    val urlJson = """{"driver":"postgresql","url":"jdbc:postgresql://localhost:5432/platform","user":"test","maximumPoolSize":10,"password":"0420b13b9aa5256f73bcf9a670acd356"}"""
    val dsc = DataSourceUtils.parseJson(urlJson)
    assert(!dsc.props.contains("driver"))

    val serverJson = """{"driver":"postgresql","serverName":"localhost","user":"test","databaseName":"postgres","password":"0420b13b9aa5256f73bcf9a670acd356","portNumber":5432}"""
    val dsc2 = DataSourceUtils.parseJson(urlJson)
    assert(!dsc2.props.contains("driver"))

  }

}
