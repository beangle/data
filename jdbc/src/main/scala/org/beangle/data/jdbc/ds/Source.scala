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

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.engine.{Engine, Engines}
import org.beangle.data.jdbc.meta.Identifier

import javax.sql.DataSource

object Source {
  def apply(dataSource: DataSource): Source = {
    val engine = Engines.forDataSource(dataSource)
    Source(engine.name, engine, dataSource, None, None)
  }

  def apply(dbconf: DatasourceConfig): Source = {
    val ds = DataSourceUtils.build(dbconf)
    val engine = Engines.forDataSource(ds)
    if Strings.isBlank(dbconf.name) then Source(engine.name, engine, ds, dbconf.catalog, dbconf.schema)
    else Source(dbconf.name, engine, ds, dbconf.catalog, dbconf.schema)
  }

  def apply(name: String, dataSource: DataSource, catalog: Option[Identifier], schema: Option[Identifier]): Source = {
    Source(name, Engines.forDataSource(dataSource), dataSource, catalog, schema)
  }
}

case class Source(name: String, engine: Engine, dataSource: DataSource, catalog: Option[Identifier], schema: Option[Identifier]) {
  def parse(schemaName: String): (Option[Identifier], Identifier) = {
    if (schemaName.isBlank) {
      (None, engine.toIdentifier(engine.defaultSchema))
    } else if (schemaName.contains(".")) {
      val c = Strings.substringBefore(schemaName, ".")
      val s = Strings.substringBefore(schemaName, ".")
      (Option(engine.toIdentifier(c)), engine.toIdentifier(s))
    } else {
      (None, engine.toIdentifier(schemaName))
    }
  }
}
