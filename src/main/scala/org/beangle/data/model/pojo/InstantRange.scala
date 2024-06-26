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

import java.time.Instant

/** 具有时间范围的实体
  *
  * 开始和结束都在有效时间范围内
  */
trait InstantRange {
  /** 起始时间 */
  var beginAt: Instant = _

  /** 结束时间 */
  var endAt: Instant = _

  def within(time: Instant): Boolean = {
    !(beginAt.isAfter(time) || endAt.isBefore(time))
  }

  def active: Boolean = within(Instant.now)
}
