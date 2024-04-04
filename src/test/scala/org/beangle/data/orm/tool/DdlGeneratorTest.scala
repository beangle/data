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

import java.io.File

import org.beangle.commons.io.Files
import org.beangle.commons.lang.SystemInfo
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class DdlGeneratorTest extends AnyFunSpec with Matchers {

  describe("DdlGenerator") {
    it("generate") {
      var target = SystemInfo.tmpDir
      if (target.endsWith(Files./)) {
        target = target.substring(0, target.length - 1)
      }
      target += Files./ + "ddl"
      DdlGenerator.main(Array("postgresql", target, "zh_CN"))
      DdlGenerator.main(Array("oracle", target, "zh_CN"))
      println(target)
    }
  }

}
