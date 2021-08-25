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

import org.beangle.commons.lang.ClassLoaders
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

import scala.xml.XML

/**
 * @author chaostone
 */
class DatasourceConfigTest extends AnyFlatSpec with Matchers {
  "DatasourceConfig " should "build a correct orace datasource" in {
    val is = ClassLoaders.getResourceAsStream("datasources.xml").get
    val config = DataSourceUtils.parseXml(is, "tigre")
    assert(config.props.contains("driverType"))
  }
  "DataSourceUtils " should "build single datasource" in {
    val ds = XML.load(ClassLoaders.getResource("single.xml").get)
    val config = DataSourceUtils.parseXml(ds)
    assert(config.props.contains("driverType"))
  }
}
