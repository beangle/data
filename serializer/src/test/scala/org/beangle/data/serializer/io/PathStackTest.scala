package org.beangle.data.serializer.io

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class PathStackTest extends FunSpec with Matchers {

  describe("PathStack") {
    it("push and pop") {
      val stack = new PathStack(16)
      stack.push("table")
      stack.push("tr")
      stack.push("td")
      stack.push("form")
      stack.pop()
      stack.pop()
      stack.push("td")
      stack.push("div")
      assert("table[1]/tr[1]/td[2]/div[1]" == stack.currentPath.explicit)
    }
  }
}