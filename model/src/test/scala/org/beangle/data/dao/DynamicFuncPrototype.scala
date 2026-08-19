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

import scala.language.dynamics

/** 验证：无校验的 scala.Dynamic DSL 是否支持 where { u => u.some_func() } 这类任意函数/聚合调用。
 *
 * 类型化 tracker（u: User）下 u.x 只能是 User 的真实成员；Dynamic 下 u: Prop，
 * `u.func(a, b)` 被改写为 u.applyDynamic("func")(a, b)，可表达数据库函数/聚合。
 *
 * 运行：sbt 'model/Test/runMain org.beangle.data.dao.DynamicFuncPrototype'
 */
object DynamicFuncPrototype {

  /** 表达式节点：直接以字符串累积 SQL（_ 为别名占位，与现有 Var.fillin 机制一致）
   */
  final class Expr(val sql: String) {
    override def toString: String = sql
    def equal(v: Any): String = s"$sql = ${lit(v)}"
    def gt(v: Any): String = s"$sql > ${lit(v)}"
    def like(v: String): String = s"$sql like $v"
    private def lit(v: Any): String = v match {
      case s: String => "'" + s + "'"
      case x         => x.toString
    }
  }

  /** 属性路径 + 函数调用：selectDynamic 累积路径，applyDynamic 组装函数
   */
  final class Prop(val path: String) extends Dynamic {
    def selectDynamic(name: String): Prop =
      new Prop(if (path.isEmpty) name else path + "." + name)

    def applyDynamic(name: String)(args: Any*): Expr = {
      val rendered = args.map {
        case p: Prop => if (p.path.isEmpty) "?" else "_." + p.path
        case e: Expr => e.sql
        case s: String => "'" + s + "'"
        case x         => x.toString
      }.mkString(", ")
      new Expr(s"$name($rendered)")
    }
  }

  def main(args: Array[String]): Unit = {
    val u = new Prop("")
    // 用户给出的形态：u.some_func()
    println("[fn] " + u.some_func());
    // 数据库函数 + 路径参数
    println("[fn] " + u.lower(u.name).equal("abc"));
    println("[fn] " + u.substr(u.name, 1, 3).equal("bil"));
    println("[fn] " + u.date_trunc("day", u.createdAt).equal("2024-01-01"));
    // 聚合
    println("[fn] " + u.count(u.roles).gt(0));
    println("[fn] " + u.sum(u.money).gt(100));
    // 嵌套函数
    println("[fn] " + u.coalesce(u.a, u.default(u.b)).gt(1));
    println("DYNAMIC FUNC PROTOTYPE OK");
  }
}