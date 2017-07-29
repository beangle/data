/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2017, Beangle Software.
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
package org.beangle.data.jdbc.dialect

import scala.collection.mutable

import org.beangle.commons.lang.Strings
import org.beangle.data.jdbc.vendor.VendorInfo
import org.beangle.data.jdbc.vendor.Vendors
import org.beangle.commons.lang.ClassLoaders

object Dialects {

  val registeredDialects = new mutable.HashMap[VendorInfo, List[Dialect]]

  def getDialect(vendor: VendorInfo, version: String): Option[Dialect] = {
    for (dialects <- registeredDialects.get(vendor))
      return dialects.find(d => d.support(version))
    None
  }

  def register(product: VendorInfo, dialects: Dialect*) {
    registeredDialects.put(product, dialects.toList)
  }

  def forName(dialectName: String): Dialect = {
    var name = Strings.capitalize(dialectName)
    name = name.replace("sql", "SQL")
    ClassLoaders.newInstance("org.beangle.data.jdbc.dialect." + name + "Dialect").asInstanceOf[Dialect]
  }

  register(Vendors.oracle, new OracleDialect)
  register(Vendors.db2, new DB2Dialect)
  register(Vendors.derby, new DerbyDialect)
  register(Vendors.h2, new H2Dialect)
  register(Vendors.hsql, new HSQLDialect)
  register(Vendors.mysql, new MySQLDialect)
  register(Vendors.postgresql, new PostgreSQLDialect)
  register(Vendors.sqlserver, new SQLServerDialect)
}
