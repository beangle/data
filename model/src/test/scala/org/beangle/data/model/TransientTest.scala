/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
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
      val m = BeanManifest.load(classOf[NumIdBean]).properties.get("persisted")
      assert(None != m)
      assert(m.get.isTransient)
      val mis = ClassInfo.get(classOf[NumIdBean]).getMethods("persisted")
      assert(mis.size == 1)
      val mi = mis.head
      val anns = mi.method.getAnnotations
      assert(null != anns && anns.length == 1)
    }
  }
}

class NumIdBean extends Entity[Integer] {
  var id: Integer = _
}
