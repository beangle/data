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
package org.beangle.data.jdbc.engine

import javax.sql.DataSource
import org.beangle.commons.lang.Strings

import scala.collection.mutable

object Engines {

  private val name2Engines = new mutable.HashMap[String, Engine]

  private def register(engines: Engine*): Unit = {
    engines.foreach { engine =>
      name2Engines.put(engine.name, engine)
    }
  }

  register(new PostgreSQL, new MySQL, new H2, new HSQL, new Oracle, new DB2, new SQLServer)

  def forDataSource(ds: DataSource): Engine = {
    val connection = ds.getConnection
    val name = connection.getMetaData.getDatabaseProductName
    connection.close()
    forName(name)
  }

  def forName(databaseName: String): Engine = {
    var name = Strings.capitalize(databaseName)
    name = name.replace("sql", "SQL")
    name2Engines.get(name) match {
      case Some(engine) => engine
      case None =>
        if (databaseName.startsWith("DB2")) {
          name2Engines("DB2")
        } else if (databaseName.startsWith("Microsoft SQL Server")) {
          name2Engines("Microsoft SQL Server")
        } else {
          throw new RuntimeException(s"Cannot find engine for database $databaseName")
        }
    }
  }

  def H2: Engine = {
    forName("H2")
  }

  def MySQL: Engine = {
    forName("MySQL")
  }

  def PostgreSQL: Engine = {
    forName("PostgreSQL")
  }

  def Oracle: Engine = {
    forName("Oracle")
  }

  def DB2: Engine = {
    forName("DB2")
  }

  def HSQL: Engine = {
    forName("HSQL Database Engine")
  }

  def SQLServer: Engine = {
    forName("Microsoft SQL Server")
  }
}
