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

package org.beangle.data.model.pojo

import org.beangle.commons.collection.Collections

import java.time.LocalDate
import scala.collection.mutable

/** 有时效性的实体
 *
 * 指有具体生效时间和失效时间的实体。一般生效时间不能为空，失效时间可以为空。
 * 具体时间采用时间时间格式便于比对。
 *
 * @author chaostone
 */
trait TemporalOn {

  /** 起始日期 */
  var beginOn: LocalDate = _

  /** 结束日期 */
  var endOn: Option[LocalDate] = None

  def within(date: LocalDate): Boolean = {
    !(beginOn.isAfter(date) || endOn.exists(_.isBefore(date)))
  }

  def active: Boolean = within(LocalDate.now)
}

object TemporalOn {

  def calcEndOn[T <: TemporalOn](journals: Iterable[T]): collection.Seq[T] = {
    if (journals.size > 1) {
      val dateMap = journals.groupBy(_.beginOn)
      val dates: mutable.Buffer[LocalDate] = dateMap.keys.toBuffer.sorted
      val rs = Collections.newBuffer[T]
      var i = 0
      while (i < dates.length - 1) { //最后一个不处理
        val ds = dateMap(dates(i))
        val jNext = dates(i + 1)
        ds.foreach { j => j.endOn = Some(jNext.minusDays(1)) }
        rs.addAll(ds)
        i += 1
      }
      rs.addAll(dateMap(dates.last))
      rs
    } else {
      journals.toSeq
    }
  }
}
