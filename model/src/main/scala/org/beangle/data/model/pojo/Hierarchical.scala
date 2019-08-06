/*
 * Beangle, Agile Development Scaffold and Toolkits.
 *
 * Copyright © 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.model.pojo

import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.{Numbers, Strings}

/** Hierarchical Entity
  * @author chaostone
  */
trait Hierarchical[T] extends Ordered[T] {

  /** index no */
  var indexno: String = _

  /** 父级菜单 */
  var parent: Option[T] = None

  var children: collection.mutable.Buffer[T] = Collections.newBuffer[T]

  def depth: Int = {
    Strings.count(indexno, ".") + 1
  }

  def lastindex: Int = {
    var index = Strings.substringAfterLast(indexno, ".")
    if (Strings.isEmpty(index)) index = indexno
    var idx = Numbers.toInt(index)
    if (idx <= 0) idx = 1
    idx
  }

  def compare(that: T): Int = {
    this.indexno.compareTo(that.asInstanceOf[Hierarchical[_]].indexno)
  }
}
