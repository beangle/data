/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.orm

import org.beangle.data.orm.model.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class AccessProxyTest extends AnyFunSpec, Matchers {
  describe("AccessProxy") {
    it("access class") {
      AccessProxy.of(classOf[TestRole])
      val user1 = AccessProxy.of(classOf[TestUser])
      val ps = user1.properties
      val u = user1.member.middleName
      val r = user1.role
      val rn = user1.role.name
      val accessed = user1.ctx.accessed()
      assert(accessed.size == 3)
      assert(accessed.contains("properties"))
      assert(accessed.contains("member.middleName"))
      assert(accessed.contains("role.name"))
    }
    it("access interface") {
      val code = AccessProxy.of(classOf[Coded])
      assert(code.code == null)
      val accessed = code.ctx.accessed()
      assert(accessed.size == 1)
      assert(accessed.contains("code"))
    }
    it("generate proxy") {
      val proxy1 = AccessProxy.of(classOf[TestUser])
      val user1 = proxy1.asInstanceOf[TestUser]
      //when we make id():long method in proxy,
      //this expression will not invoke id method,just direct get id field.
      assert(user1.id == 0L)
      assert(null != user1.member)
      assert(null != user1.member.name)
      assert(user1.member.name.firstName == null)

      val accessed = proxy1.ctx.accessed()
      assert(accessed.size >= 2)
      assert(accessed.contains("member.name.firstName"))

      val user2 = AccessProxy.of(classOf[TestUser]).asInstanceOf[TestUser]
      assert(user2.member != user1.member)
    }
  }

}
