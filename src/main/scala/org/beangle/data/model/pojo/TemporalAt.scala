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

/** 有时效性的实体
 *
 * 指有具体生效时间和失效时间的实体。一般生效时间不能为空，失效时间可以为空。
 * 具体时间采用时间时间格式便于比对。
 *
 * @author chaostone
 */
trait TemporalAt {

  /** 获得生效时间 */
  var beginAt: Instant = _

  /** 获得失效时间 */
  var endAt: Option[Instant] = None

  def within(time: Instant): Boolean = {
    !(beginAt.isAfter(time) || endAt.exists(_.isBefore(time)))
  }

  def active: Boolean = within(Instant.now)
}
