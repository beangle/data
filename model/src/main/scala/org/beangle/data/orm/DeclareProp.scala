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

package org.beangle.data.orm

import org.beangle.data.dao.Prop
import org.beangle.data.orm.MappingModule.{EntityHolder, PropertyDeclaration}

/** 绑定期 declare DSL 的 scala.Dynamic 属性路径。
 *
 * 与 OQL 侧共用 [[Prop]] 的 selectDynamic 路径累积机制：`e.name.first` 编译为
 * `e.selectDynamic("name").selectDynamic("first")`，本类直接把路径作用于
 * EntityHolder 的属性声明（is/&/are），替代原有的逐实体 $Tracker 子类。
 */
class DeclareProp(path: String, holder: EntityHolder[_]) extends Prop(path) {

  override def selectDynamic(name: String): DeclareProp =
    new DeclareProp(if (path.isEmpty) name else path + "." + name, holder)

  /** 属性声明：e.name.first is(notnull, length(20))
   */
  def is(declarations: PropertyDeclaration*): Unit = {
    if (declarations.nonEmpty && path.isEmpty) {
      throw new RuntimeException(s"Cannot find access properties for ${holder.mapping.entityName} with declarations: $declarations")
    }
    val pm = holder.mapping.property(path)
    declarations foreach (d => d(holder, path, pm))
    //if the property is declared explicitly, don't change it
    pm.mergeable = false
  }

  /** 组合声明：e.name.first & e.name.last & e.createdOn are notnull
   */
  def &(next: DeclareProp): DeclareProps = new DeclareProps(holder, List(this, next))
}

class DeclareProps(holder: EntityHolder[_], val props: List[DeclareProp]) {
  def &(next: DeclareProp): DeclareProps = new DeclareProps(holder, props :+ next)
  def are(declarations: PropertyDeclaration*): Unit = props.foreach(_.is(declarations*))
}
