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

package org.beangle.data.dao

import org.beangle.data.dao.OqlBuilder.{Expression, Var}

import scala.language.dynamics

/** OQL 属性路径（scala.Dynamic）。
 *
 * OqlBuilder.where/on 的 lambda 参数为 Prop 时，`e.name.first` 被编译器改写为
 * `e.selectDynamic("name").selectDynamic("first")`，路径以字符串累积，无需为每个实体生成
 * $Tracker 子类；`e.func(a, b)` 改写为 `e.applyDynamic("func")(a, b)`，可表达数据库函数/聚合。
 *
 * 路径以 `_.` 为别名占位（与 [[OqlBuilder.Var.fillin]] 一致），渲染时替换为实际别名。
 */
class Prop(val path: String) extends Dynamic {

  /** e.name.first -> selectDynamic("name").selectDynamic("first")，累积路径 */
  def selectDynamic(name: String): Prop =
    new Prop(if (path.isEmpty) name else path + "." + name)

  /** 数据库函数/聚合调用：u.lower(u.name) -> Var("lower(_.name)") */
  def applyDynamic(name: String)(args: Any*): Var =
    Var(name + "(" + args.map(Prop.render).mkString(", ") + ")")

  /** 别名占位形式：_.department.id（空路径返回 "_."） */
  def placeholder: String = if (path.isEmpty) "_." else "_." + path

  def toVar: Var = Var(placeholder)

  def fillin(alias: String): String = toVar.fillin(alias)

  /** 函数式包装：e.id.f("avg(_)") -> Var("avg(_.id)") */
  def f(func: String): Var = toVar.f(func)

  // ---- 条件算子（委托给 Var，沿用其 SQL 渲染） ----
  def isNull: Expression = toVar.isNull
  def isNotNull: Expression = toVar.isNotNull
  def like(value: String): Expression = toVar.like(value)
  def equal(arg: Any): Expression = toVar.equal(arg)
  def gt(arg: Any): Expression = toVar.gt(arg)
  def ge(arg: Any): Expression = toVar.ge(arg)
  def lt(arg: Any): Expression = toVar.lt(arg)
  def le(arg: Any): Expression = toVar.le(arg)
  def is(exp: String, args: Any*): Expression = toVar.is(exp, args*)
  def between(start: Any, end: Any): Expression = toVar.between(start, end)
  def in(args: Any*): Expression = toVar.in(args*)

  /** 集合包含：统一翻译为 ? in elements(_.path)
   *  - Map 路径：elements = values（例：e.properties.contains("some street")）
   *  - Set/Seq/实体集合：elements = 元素（实体集合需传实体对象，传 id 会类型不匹配）
   */
  def contains(value: Any): Expression =
    new Expression(s"? in elements($placeholder)", Seq(value))

  /** Map 键包含：? in indices(_.path)（indices = Map 的 keys）
   * 例：e.times.containsKey(1) -> ? in indices(_.times)
   */
  def containsKey(key: Any): Expression =
    new Expression(s"? in indices($placeholder)", Seq(key))

  override def toString: String = placeholder
}

object Prop {
  private[dao] def render(a: Any): String = a match {
    case p: Prop => p.placeholder
    case v: Var => v.name
    case s: String => "'" + s + "'"
    case x => x.toString
  }
}
