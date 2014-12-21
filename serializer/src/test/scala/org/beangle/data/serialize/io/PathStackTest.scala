package org.beangle.data.serialize.io

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PathStackTest extends FunSpec with Matchers {

  describe("PathStack") {
    it("push and pop") {
      val stack = new PathStack(16)
      stack.push("table",null)
      stack.push("tr",null)
      stack.push("td",null)
      stack.push("form",null)
      stack.pop()
      stack.pop()
      stack.push("td",null)
      stack.push("div",null)
      assert("table[1]/tr[1]/td[2]/div[1]" == stack.currentPath.explicit)
    }
  }
}