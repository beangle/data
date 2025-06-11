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

import java.time.{LocalDate, YearMonth}

/** 年月范围
 */
trait YearMonthRange {

  /** 起始年月 */
  var beginIn: YearMonth = _

  /** 结束年月 */
  var endIn: YearMonth = _

  def within(date: LocalDate): Boolean = {
    within(YearMonth.of(date.getYear, date.getMonth))
  }

  def within(ym: YearMonth): Boolean = {
    !(beginIn.isAfter(ym) || endIn.isBefore(ym))
  }

  def active: Boolean = within(YearMonth.now)
}
