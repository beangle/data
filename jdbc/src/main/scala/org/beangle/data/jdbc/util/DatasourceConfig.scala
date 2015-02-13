/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2014, Beangle Software.
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
package org.beangle.data.jdbc.util

import org.beangle.data.jdbc.dialect.Dialect
import org.beangle.commons.bean.Initializing
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.ClassLoaders
import java.util.Properties

object DatasourceConfig {

  def build(xml: scala.xml.Node): DatasourceConfig = {
    val dbconf = new DatasourceConfig(ClassLoaders.loadClass((xml \\ "dialect").text.trim).newInstance().asInstanceOf[Dialect])
    dbconf.url = (xml \\ "url").text.trim
    if (!(xml \ "@name").isEmpty) dbconf.name = (xml \ "@name").text.trim
    dbconf.user = (xml \\ "user").text.trim
    dbconf.driver = (xml \\ "driver").text.trim
    dbconf.password = (xml \\ "password").text.trim
    dbconf.schema = (xml \\ "schema").text.trim
    dbconf.catalog = (xml \\ "catalog").text.trim
    if (Strings.isEmpty(dbconf.catalog)) dbconf.catalog = null
    (xml \\ "db" \\ "props" \\ "prop").foreach { ele =>
      dbconf.props.put((ele \ "@name").text, (ele \ "@value").text);
    }
    dbconf.init()
    dbconf
  }
}

class DatasourceConfig(val dialect: Dialect) extends Initializing {
  var name: String = _
  var url: String = _
  var user: String = _
  var password: String = _
  var driver: String = _
  var props: Properties = new Properties
  var schema: String = _
  var catalog: String = _

  def init() {
    if (Strings.isEmpty(schema)) {
      schema = dialect.defaultSchema
      if (schema == "$user") schema = user
    }
    if (driver.endsWith("OracleDriver")) schema = schema.toUpperCase()
  }
}
