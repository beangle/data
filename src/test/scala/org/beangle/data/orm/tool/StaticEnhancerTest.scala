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

package org.beangle.data.orm.tool

import org.beangle.commons.io.ResourcePatternResolver
import org.beangle.commons.lang.SystemInfo
import org.beangle.data.orm.Mappings
import org.beangle.jdbc.engine.Engines
import org.beangle.jdbc.meta.Database

import java.io.File
import java.util.Locale

object StaticEnhancerTest {
  def main(args: Array[String]): Unit = {
    val engine = Engines.forName("PostgreSQL")
    val ormLocations = ResourcePatternResolver.getResources("classpath*:META-INF/beangle.xml")
    val database = new Database(engine)
    val mappings = new Mappings(database, ormLocations)
    mappings.locale = Locale.SIMPLIFIED_CHINESE
    mappings.autobind()

    val se = new StaticEnhancer
    se.enhance(new File(SystemInfo.tmpDir), mappings)
  }
}
