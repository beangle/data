package org.beangle.data.hibernate

import org.beangle.commons.lang.SystemInfo
import org.beangle.data.orm.tool.DdlGenerator
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DdlGeneratorTest extends AnyFunSpec with Matchers {

  describe("DdlGenerator") {
    it("generate") {
      val dir = SystemInfo.tmpDir
      DdlGenerator.main(Array("postgresql", dir, "zh_CN"))
      println("genderate ddl in " + dir)
    }
  }

}
