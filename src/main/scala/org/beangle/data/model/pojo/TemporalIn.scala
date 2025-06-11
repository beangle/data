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

import java.time.{LocalDate, YearMonth}
import scala.collection.mutable

trait TemporalIn {

  /** 起始日期 */
  var beginIn: YearMonth = _

  /** 结束日期 */
  var endIn: Option[YearMonth] = None

  def within(date: LocalDate): Boolean = {
    within(YearMonth.of(date.getYear, date.getMonth))
  }

  def within(ym: YearMonth): Boolean = {
    !(beginIn.isAfter(ym) || endIn.exists(_.isBefore(ym)))
  }

  def active: Boolean = within(YearMonth.now)
}

object TemporalIn {

  def calcEndIn[T <: TemporalIn](journals: Iterable[T]): collection.Seq[T] = {
    if (journals.size > 1) {
      val dateMap = journals.groupBy(_.beginIn)
      val dates: mutable.Buffer[YearMonth] = dateMap.keys.toBuffer.sorted
      val rs = Collections.newBuffer[T]
      var i = 0
      while (i < dates.length - 1) { //最后一个不处理
        val ds = dateMap(dates(i))
        val jNext = dates(i + 1)
        ds.foreach { j => j.endIn = Some(jNext.minusMonths(1)) }
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
