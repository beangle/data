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

package org.beangle.data.orm.model

import org.beangle.commons.lang.annotation.value

object CreditHours {
  val Empty = CreditHours(0)
}

// 由于AnyVal的类型擦除的原因，使用CreditHours的类中生成的set方法中参数的实际类型是Int,
// 而且AnyVal不能进行运行时类型检测，所以，不容易通过反射进行设置
// 所以不要使用继承AnyVal
@value
class CreditHours(val hours: Int) extends Serializable {

}
