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

package org.beangle.data.jdbc.engine

import org.beangle.commons.lang.Strings

import javax.sql.DataSource
import scala.collection.mutable

object Engines {

  private val name2Engines = new mutable.HashMap[String, List[Engine]]

  private def register(engines: Engine*): Unit = {
    engines.foreach { engine =>
      val engList = name2Engines.get(engine.name) match {
        case Some(el) => el.appended(engine)
        case None => List(engine)
      }
      name2Engines.put(engine.name, engList)
    }
  }

  register(new PostgreSQL10, new MySQL5, new H2, new Oracle10g, new Oracle12c, new DB2V8, new SQLServer2005,
    new SQLServer2008, new SQLServer2012, new Derby10)

  def forDataSource(ds: DataSource): Engine = {
    val connection = ds.getConnection
    val engine = forMetadata(connection.getMetaData)
    connection.close()
    engine
  }

  def forMetadata(meta: java.sql.DatabaseMetaData): Engine = {
    val name = meta.getDatabaseProductName
    val version = s"${meta.getDatabaseMajorVersion}.${meta.getDatabaseMinorVersion}"
    forName(name, version)
  }

  def forName(dbname: String, version: String = "last"): Engine = {
    var name = Strings.capitalize(dbname)
    name = name.replace("sql", "SQL")
    val engineList = name2Engines.get(name) match {
      case Some(el) => el
      case None =>
        if (dbname.toUpperCase.startsWith("DB2")) {
          name2Engines("DB2")
        } else if (dbname.toUpperCase.startsWith("SQLSERVER") || dbname.startsWith("Microsoft SQL Server")) {
          name2Engines("Microsoft SQL Server")
        } else List.empty
    }
    if version == "last" then
      engineList.last
    else
      val engine = engineList.findLast(e => e.version.contains(version))
      if engine.isEmpty then throw new RuntimeException(s"Cannot find engine for database $dbname")
      else engine.head
  }
}
