package org.beangle.data.jpa.hibernate

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IdFuncTest extends FunSpec with Matchers {

  describe("IdFunc") {
    it("generate id") {
      val i1 = LongSecondId.gen(2014, 2)
      assert(i1.getClass() == classOf[java.lang.Long])
      assert(LongDateId.gen(2013, 2).longValue > 0)

      val i2 = IntYearId.gen(2014, 1)
      assert(i2.getClass() == classOf[java.lang.Integer])
      assert(i2.intValue() == 201400001)

      val i3 = LongYearId.gen(2014, 1)
      assert(i3.getClass() == classOf[java.lang.Long])
      assert(i3 == 201400000000000001L)
    }
  }
}