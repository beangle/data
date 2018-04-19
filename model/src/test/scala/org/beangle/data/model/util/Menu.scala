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
package org.beangle.data.model.util

import org.beangle.data.model.pojo.Hierarchical
import org.beangle.data.model.IntId
import org.beangle.commons.collection.Collections
import org.beangle.commons.lang.Strings
import scala.collection.mutable.Buffer

object Menu {
  def apply(id: Int, indexno: String): Menu = {
    val menu = new Menu()
    menu.id = id
    menu.indexno = indexno
    menu
  }
  def apply(id: Int, indexno: String, parent: Menu): Menu = {
    val menu = new Menu()
    menu.id = id
    menu.indexno = indexno
    menu.parent = Some(parent)
    parent.children += menu
    menu
  }
}

class Menu extends IntId with Hierarchical[Menu] {
  override def toString = {
    "id:" + id + " indexno:" + indexno
  }
}

class Profile {
  private val menus = Collections.newBuffer[Menu]

  def menu(id: Int): Menu = {
    menus.find(m => m.id == id).get
  }
  def tops(): Buffer[Menu] = {
    menus.filter(m => m.parent == None)
  }

  def move(menuId: Int, parentId: Option[Int], idx: Int): Iterable[Menu] = {
    val me = menu(menuId)
    var parent: Menu = null
    var sibling: Buffer[Menu] = null
    parentId match {
      case Some(p) =>
        parent = menu(p); sibling = parent.children
      case None => sibling = tops()
    }
    if (null == parent) {
      Hierarchicals.move(me, sibling, idx)
    } else {
      Hierarchicals.move(me, parent, idx)
    }
  }
  def add(id: Int, indexNo: String): this.type = {
    val menu = Menu(id, indexNo)
    val parentIndexNo = Strings.substringBeforeLast(indexNo, ".")
    if (indexNo.contains(".") && "" == parentIndexNo) {
      throw new RuntimeException("invalid index no")
    }
    menu.parent = menus.find(x => x.indexno == parentIndexNo)
    menu.parent foreach { p =>
      p.children += menu
    }

    this.menus += menu
    this
  }
}
