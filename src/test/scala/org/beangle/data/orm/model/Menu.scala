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

import org.beangle.data.model.pojo.{Hierarchical, Named}
import org.beangle.data.model.{Entity, LongId, LongIdEntity}

trait Menu extends Named with LongIdEntity{
  var parent: Option[Menu] = None
}

abstract class AbstractMenu extends LongId with Menu {
  @transient var someVar: String = _
  var title: String = _
}

class UrlMenu extends AbstractMenu {
  var url: String = _
}
