package org.beangle.data.model

import org.junit.runner.RunWith
import org.scalatest.{ FunSpec, Matchers }
import org.scalatest.junit.JUnitRunner
import org.beangle.commons.lang.reflect.BeanManifest
import java.beans.Transient
import org.beangle.commons.lang.reflect.ClassInfo

@RunWith(classOf[JUnitRunner])
class TransientTest extends FunSpec with Matchers {

  describe("Entity") {
    it("transient persisted property") {
      assert(None == BeanManifest.load(classOf[NumIdBean]).getGetter("persisted"))
      val mis = ClassInfo.get(classOf[NumIdBean]).getMethods("persisted")
      assert(mis.size == 1)
      val mi = mis.head
      val anns = mi.method.getAnnotations
      assert(null != anns && anns.length == 1)
      assert(mi.method.isAnnotationPresent(classOf[Transient]))
    }
  }
}

class NumIdBean extends Entity[Integer] {
  var id: Integer = _
}
