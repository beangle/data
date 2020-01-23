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

/**
  * @author chaostone
  */
@RunWith(classOf[JUnitRunner])
class DataSourceFactoryTest extends AnyFlatSpec with Matchers {

  "DataSourceFactory " should "build a datasource" in {
    val factory = new DataSourceFactory()
    factory.url = ClassLoaders.getResource("datasources.xml").get.toString
    factory.name = "h2"
    factory.init()
    factory.result.getConnection
    factory.destroy()
  }
}
