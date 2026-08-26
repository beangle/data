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

import org.beangle.commons.lang.reflect.BeanInfos
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class TempIdTypeTest extends AnyFunSpec, Matchers {
  describe("id type resolution") {
    it("print BeanInfos id types") {
      val classes = Seq(
        classOf[org.beangle.data.orm.model.LongIdResource],
        classOf[org.beangle.data.orm.model.IntIdResource],
        classOf[org.beangle.data.orm.model.Coded],
        classOf[org.beangle.data.orm.model.User],
        classOf[org.beangle.data.orm.model.Profile],
        classOf[org.beangle.data.orm.model.SkillType],
        classOf[org.beangle.data.orm.model.Course],
        classOf[org.beangle.data.orm.model.CourseLevel],
        classOf[org.beangle.data.orm.model.Role])
      classes.foreach { c =>
        val bi = BeanInfos.get(c)
        val t = bi.getPropertyType("id").map(_.getName).getOrElse("null")
        println(s"${c.getSimpleName} id=$t")
      }
    }
  }
}
