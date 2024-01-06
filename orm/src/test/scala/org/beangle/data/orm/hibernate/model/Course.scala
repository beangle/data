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
import org.beangle.commons.lang.time.WeekState
import org.beangle.data.model.pojo.Named
import org.beangle.data.model.{Component, LongId}

import java.time.{Instant, LocalDate}
import scala.collection.mutable

class Course extends LongId, Named {

  var levels: mutable.Set[CourseLevel] = Collections.newSet[CourseLevel]

  var features: mutable.Buffer[CourseFeature] = Collections.newBuffer[CourseFeature]

  var weekstate: WeekState = WeekState.Zero

  var category: Option[CourseCategory] = None

  var credits: Option[Int] = None

  var hours: CreditHours = CreditHours.Empty

  def addFeature(name: String, description: String): Unit = {
    features.find(x => x.name == name) match
      case None =>
        val f = new CourseFeature
        f.name = name
        f.description = description
        f.createdAt = Instant.now
        features.addOne(f)
      case Some(f) =>
        f.description = description
        f.createdAt = Instant.now
  }

  def hasFeature(name: String, description: String): Boolean = {
    features.find(x => x.name == name) match
      case None => false
      case Some(f) => f.description == description
  }

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

class CourseFeature extends Component {
  var name: String = _
  var createdAt: Instant = _
  var description: String = _
}

enum CourseCategory(val id: Int) {
  case Theoretical extends CourseCategory(1)
  case Practical extends CourseCategory(2)

}
