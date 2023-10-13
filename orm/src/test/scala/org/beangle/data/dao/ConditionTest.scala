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

package org.beangle.data.dao

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ConditionTest extends AnyFunSpec with Matchers {

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
  describe("Conditions") {
    it("split") {
      var rs = Conditions.split("role, ^\"admin,root,user1\" ， ^user2$", classOf[String])
      assert(rs.length == 3)
      assert(rs(0) == "role")
      assert(rs(1) == "^\"admin,root,user1\"")
      assert(rs(2) == "^user2$")

      rs = Conditions.split("ro le, ^\"admin, root,user1\" ， ^user2$", classOf[String])
      assert(rs.length == 4)
      assert(rs(0) == "ro")
      assert(rs(1) == "le")
      assert(rs(2) == "^\"admin, root,user1\"")
      assert(rs(3) == "^user2$")

      rs = Conditions.split("ro le\t ^\"admin root\tuser1\" \n ^user2$", classOf[String])
      assert(rs.length == 4)
      assert(rs(0) == "ro")
      assert(rs(1) == "le")
      assert(rs(2) == "^\"admin root\tuser1\"")
      assert(rs(3) == "^user2$")

      rs = Conditions.split("ro le us er", classOf[String])
      assert(rs.length == 4)
      assert(rs(0) == "ro")
      assert(rs(1) == "le")
      assert(rs(2) == "us")
      assert(rs(3) == "er")
    }
    it("parse") {
      var c = Conditions.parse("user.name", " admin ", classOf[String])
      assert(c.content == "user.name like :user_name")
      assert(c.params.head == "%admin%")

      c = Conditions.parse("user.name", "admin , root", classOf[String])
      assert(c.content == "user.name like :user_name_1 or user.name like :user_name_2")
      assert(c.params.head == "%admin%")
      assert(c.params.last == "%root%")

      c = Conditions.parse("user.name", "admin,root, user1, user2", classOf[String])
      assert(c.content == "user.name in (:user_name)")
      assert(c.params.head.asInstanceOf[Iterable[_]].toList == List("admin", "root", "user1", "user2"))

      c = Conditions.parse("user.name", "\"admin,root,user1\",user2", classOf[String])
      assert(c.content == "user.name like :user_name_1 or user.name like :user_name_2")
      assert(c.params.head == "%admin,root,user1%")
      assert(c.params.last == "%user2%")

      c = Conditions.parse("user.name", "role,^\"admin,root,user1\",user2$", classOf[String])
      assert(c.content == "user.name like :user_name_1 or user.name like :user_name_2 or user.name like :user_name_3")
      assert(c.params.head == "%role%")
      assert(c.params(1) == "admin,root,user1%")
      assert(c.params.last == "%user2")

      c = Conditions.parse("user.name", "null,\" \",^user2$", classOf[String])
      assert(c.content == "user.name is null or user.name like :user_name_1 or user.name = :user_name_2")
      assert(c.params.head == "% %")
      assert(c.params.last == "user2")

      c = Conditions.parse("user.name", "null,^ admin,^user2$", classOf[String])
      assert(c.content == "user.name is null or user.name like :user_name_1 or user.name = :user_name_2")
      assert(c.params.head == " admin%")
      assert(c.params.last == "user2")

      c = Conditions.parse("user.name", " null ", classOf[String])
      assert(c.content == "user.name is null")
      assert(c.params.isEmpty)

      c = Conditions.parse("user.name", "^admin", classOf[String])
      assert(c.content == "user.name like :user_name")
      assert(c.params.head == "admin%")
    }
  }
}
