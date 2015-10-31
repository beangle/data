package org.beangle.data.model.bind

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import org.beangle.data.model.TestUser

/**
 * @author chaostone
 */
@RunWith(classOf[JUnitRunner])
class ProxyTest extends FunSpec with Matchers {
  describe("Proxy") {
    it("generate proxy") {
      val proxy1 = Proxy.generate(classOf[TestUser])
      val user1 = proxy1.asInstanceOf[TestUser]
      assert(user1.id == 0L)
      assert(null != user1.member)
      assert(null != user1.member.name)
      assert(user1.member.name.firstName == null)

      val accessed = proxy1.lastAccessed()
      assert(accessed.contains("id"))
      assert(accessed.contains("member.name.firstName"))

      val user2 = Proxy.generate(classOf[TestUser]).asInstanceOf[TestUser]
      assert(user2.member != user1.member)
    }
  }
}