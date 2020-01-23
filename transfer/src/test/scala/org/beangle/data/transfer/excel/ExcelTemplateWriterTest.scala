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
package org.beangle.data.transfer.excel

import java.nio.file.Files
import java.io.FileOutputStream

import org.junit.runner.RunWith
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.transfer.exporter.ExportContext

@RunWith(classOf[JUnitRunner])
class ExcelTemplateWriterTest extends AnyFunSpec with Matchers {
  describe("TemplateWriter") {
    it("export") {
      val template = ClassLoaders.getResource("template.xls").get
      val tempFile = Files.createTempFile("out", ".xls")
      val os = new FileOutputStream(tempFile.toFile)
      val context = new ExportContext()
      val users = List(User(1, "001"), User(2, "002"))
      context.put("users", users)
      val writer = new ExcelTemplateWriter(template, context, os)
      writer.write()
      os.close()
      //tempFile.toFile.delete()
    }
  }

}
