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

package org.beangle.data.orm.hibernate.model

import org.beangle.commons.collection.Collections
import org.beangle.data.model.LongId
import org.beangle.data.model.pojo.Named

import java.time.LocalDate
import scala.collection.mutable

class Course extends LongId, Named {

  var levels: mutable.Set[CourseLevel] = Collections.newSet[CourseLevel]

  def addLevel(l: Int): Unit = {
    val cl = new CourseLevel
    cl.course = this
    this.levels += cl
    cl.level = l
    cl.beginOn = LocalDate.now()
  }
}

class CourseLevel extends LongId {
  var course: Course = _
  var level: Int = _
  var beginOn: LocalDate = _
}
