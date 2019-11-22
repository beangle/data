/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.orm

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.junit.JUnitRunner

/**
 * @author chaostone
 */
@RunWith(classOf[JUnitRunner])
class ProxyTest extends AnyFunSpec with Matchers {
  describe("Proxy") {
    it("access without component") {
      val proxy1 = Proxy.generate(classOf[TestUser])
      val user1 = proxy1.asInstanceOf[TestUser]
      val ps = user1.properties
      val accessed = proxy1.lastAccessed
      assert(accessed.size == 1)
      assert(accessed.contains("properties"))
    }

    it("generate proxy") {
      val proxy1 = Proxy.generate(classOf[TestUser])
      val user1 = proxy1.asInstanceOf[TestUser]
      //when we make id():long method in proxy,
      //this expression will not invoke id method,just direct get id field.
      assert(user1.id == 0L)
      assert(null != user1.member)
      assert(null != user1.member.name)
      assert(user1.member.name.firstName == null)

      val accessed = proxy1.lastAccessed
      assert(accessed.size >= 2)
      assert(accessed.contains("member.name.firstName"))

      val user2 = Proxy.generate(classOf[TestUser]).asInstanceOf[TestUser]
      assert(user2.member != user1.member)
    }
  }
}
