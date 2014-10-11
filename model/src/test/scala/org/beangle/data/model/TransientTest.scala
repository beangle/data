package org.beangle.data.model

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.lang.reflect.BeanManifest
import java.beans.Transient

@RunWith(classOf[JUnitRunner])
class TransientTest extends FunSpec with Matchers {

  describe("Entity") {
    it("transient persisted property") {
      val mi = BeanManifest.load(classOf[NumIdBean]).getGetter("persisted").get
      val anns = mi.method.getAnnotations
      assert(null != anns && anns.length == 1)
      assert(mi.method.isAnnotationPresent(classOf[Transient]))
    }
  }
}

class NumIdBean extends Entity[Integer] {
  var id: Integer = _
}
