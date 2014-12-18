package org.beangle.data.model.dao

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.lang.reflect.ClassInfo

@RunWith(classOf[JUnitRunner])
class ConditionTest extends FunSpec with Matchers {

  describe("Condition") {
    it("paramNames should given list string") {
      val con = new Condition("a.id=:id and b.name=:name")
      val paramNames = con.paramNames
      assert(null != paramNames)
      assert(paramNames.size == 2)
      assert(paramNames.contains("id"))
      assert(paramNames.contains("name"))

      val con2 = new Condition(":beginOn < a.beginOn and b.name=:name")
      val paramNames2 = con2.paramNames
      assert(null != paramNames)
      assert(paramNames2.size == 2)
      assert(paramNames2.contains("beginOn"))
      assert(paramNames2.contains("name"))
    }
  }
}
