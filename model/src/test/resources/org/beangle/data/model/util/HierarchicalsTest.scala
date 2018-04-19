package org.beangle.data.model.util

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HierarchicalsTest extends FunSpec with Matchers {

  def makeMenuProfile(): Profile = {
    val p = new Profile
    p.add(1, "01")
    p.add(2, "01.01")
    p.add(3, "01.02")
    p.add(4, "01.03")
    p.add(5, "01.04")
    p.add(6, "01.05")
    p.add(7, "01.06")
    p.add(8, "01.07")
    p.add(9, "01.08")
    p.add(10, "01.09")
    p.add(11, "01.10")
    p.add(12, "01.10.01")
    p.add(13, "01.10.02")
    p.add(14, "01.10.03")
    p.add(15, "01.10.04")
    p.add(16, "01.10.05")
    p.add(17, "01.10.06")
    p.add(18, "01.10.07")
    p.add(19, "01.10.08")
    p.add(20, "01.10.09")
    p.add(21, "01.10.10")
    p.add(22, "01.11")
    p.add(23, "01.11.01")
    p.add(24, "01.11.02")
    p.add(25, "01.11.03")
    p
  }

  describe("Hierarchicals") {
    it("move to parent") {
      val p = makeMenuProfile()
      p.move(23, Some(1), 1)
      p.menu(23).indexno should be("01.01")
      p.menu(25).indexno should be("01.12.3")
    }
    it("move to top") {
      val p = makeMenuProfile()
      p.move(23, None, 2).toBuffer
      p.menu(23).indexno should be("2")
      p.menu(25).indexno should be("1.11.3")
    }

    it("move a subtree") {
      val p = makeMenuProfile()
      p.move(22, Some(10), 1).toBuffer
      p.menu(22).indexno should be("01.09.1")
      p.menu(25).indexno should be("01.09.1.3")
    }
  }
}