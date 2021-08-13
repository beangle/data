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

import java.time.LocalDate

/** 具有日期范围的实体
  *
  * 开始和结束都在有效日期范围内
  */
trait DateRange {
  /** 起始日期 */
  var beginOn: LocalDate = _

  /** 结束日期 */
  var endOn: LocalDate = _

  def within(date: LocalDate): Boolean = {
    !(beginOn.isAfter(date) || endOn.isBefore(date))
  }
}
