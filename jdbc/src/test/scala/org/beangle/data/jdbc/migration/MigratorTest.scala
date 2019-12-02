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
package org.beangle.data.jdbc.migration

import org.beangle.data.jdbc.engine.{Engines}
import org.beangle.data.jdbc.meta.Database
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MigratorTest extends AnyFunSpec with Matchers {
  describe("Migrator") {
    it("test diff") {
      val migrator = new Migrator
      val newer = new Database(Engines.PostgreSQL)
      val older = new Database(Engines.PostgreSQL)
      val diff = migrator.sql(new Diff().diff(newer, older))
    }
  }
}
