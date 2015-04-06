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
import org.beangle.data.jdbc.dialect.Name

object DatasourceConfig {

  def build(xml: scala.xml.Node): DatasourceConfig = {
    val dialect = ClassLoaders.loadClass((xml \\ "dialect").text.trim).newInstance().asInstanceOf[Dialect]
    val dbconf = new DatasourceConfig(dialect)
    dbconf.url = (xml \\ "url").text.trim
    if (!(xml \ "@name").isEmpty) dbconf.name = (xml \ "@name").text.trim
    dbconf.user = (xml \\ "user").text.trim
    dbconf.driver = (xml \\ "driver").text.trim
    dbconf.password = (xml \\ "password").text.trim
    var schemaName = (xml \\ "schema").text.trim

    dbconf.catalog = dialect.parse((xml \\ "catalog").text.trim)

    (xml \\ "db" \\ "props" \\ "prop").foreach { ele =>
      dbconf.props.put((ele \ "@name").text, (ele \ "@value").text);
    }

    if (Strings.isEmpty(schemaName)) {
      schemaName = dialect.defaultSchema
      if (schemaName == "$user") schemaName = dbconf.user
    }
    dbconf.schema = dialect.parse(schemaName)
    dbconf
  }

}

class DatasourceConfig(val dialect: Dialect) {
  var name: String = _
  var url: String = _
  var user: String = _
  var password: String = _
  var driver: String = _
  var props: Properties = new Properties
  var schema: Name = _
  var catalog: Name = _
}
