package org.beangle.data.transfer.excel

import java.nio.file.Files
import java.io.FileOutputStream

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.lang.ClassLoaders
import org.beangle.data.transfer.exporter.ExportContext

@RunWith(classOf[JUnitRunner])
class ExcelTemplateWriterTest extends FunSpec with Matchers {
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