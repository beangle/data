package org.beangle.data.jpa.hibernate.id

import java.text.SimpleDateFormat
import java.{ util => ju }
import org.junit.runner.RunWith
import org.scalatest.{ Finders, FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class IdFuncTest extends FunSpec with Matchers {

  describe("IdFunc") {
    it("generate id") {
      val generator = new LongIdFunctor("dummy")

      //generate current year
      val i1 = generator.format(2014, 2)
      assert(i1.getClass() == classOf[java.lang.Long])
      val i3 = generator.format(2014, 1)
      var now = new SimpleDateFormat("yyyyMMddHHmm").format(ju.Calendar.getInstance.getTime)
      assert(i3.getClass() == classOf[java.lang.Long])
      assert(i3 == java.lang.Long.valueOf(now + "000001"))

      //generator past year
      now = new SimpleDateFormat("yyyyMMdd").format(ju.Calendar.getInstance.getTime)
      val id = generator.format(2013, 2)
      assert(id.longValue > 0)
      assert(id.toString.contains(now))
    }
  }
}