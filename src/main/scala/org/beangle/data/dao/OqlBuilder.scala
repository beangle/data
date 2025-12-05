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

import org.beangle.commons.lang.Strings.*
import org.beangle.commons.lang.{ClassLoaders, Strings}
import org.beangle.data.dao.OqlBuilder.{Expression, Var}
import org.beangle.data.orm.Jpas

object OqlBuilder {

  def oql[E](oql: String): OqlBuilder[E] = {
    val query = new OqlBuilder[E]()
    query.statement = oql
    query
  }

  def from[E](from: String): OqlBuilder[E] = {
    val query = new OqlBuilder[E]()
    query.newFrom(from)
    query
  }

  def from[E](entityName: String, alias: String): OqlBuilder[E] = {
    val query = new OqlBuilder[E]()
    query.entityClass = ClassLoaders.load(entityName).asInstanceOf[Class[E]]
    query.tracker = AccessTracker.of(query.entityClass)
    query.alias = alias
    query.select = "select " + alias
    query.from = concat("from ", entityName, " ", alias)
    query
  }

  def from[E](entityClass: Class[E]): OqlBuilder[E] = {
    from(entityClass, uncapitalize(substringAfterLast(Jpas.findEntityName(entityClass), ".")))
  }

  def from[E](entityClass: Class[E], alias: String): OqlBuilder[E] = {
    val query = new OqlBuilder[E]()
    query.entityClass = entityClass
    query.tracker = AccessTracker.of(entityClass)
    query.alias = alias
    query.select = "select " + alias
    query.from = concat("from ", Jpas.findEntityName(entityClass), " ", alias)
    query
  }

  def sum(v: Var): Var = {
    Var(s"sum(${v.name})")
  }

  def avg(v: Var): Var = {
    Var(s"avg(${v.name})")
  }

  def max(v: Var): Var = {
    Var(s"max(${v.name})")
  }

  def min(v: Var): Var = {
    Var(s"min(${v.name})")
  }

  def distinct(v: Var): Var = {
    Var(s"distinct ${v.name}")
  }

  def count(v: Var): Var = {
    Var(s"count(${v.name})")
  }

  case class Var(name: String) {

    def isNull: Expression = create("is null")

    def isNotNull: Expression = create("is not null")

    def like(value: String): Expression = {
      var arg = value
      if (!arg.startsWith("%") && !arg.endsWith("%")) {
        arg = "%" + arg + "%"
      }
      create("like ?", arg)
    }

    def equal(arg: Any): Expression = create("= ?", arg)

    def gt(arg: Any): Expression = create("> ?", arg)

    def ge(arg: Any): Expression = create(">= ?", arg)

    def lt(arg: Any): Expression = create("< ?", arg)

    def le(arg: Any): Expression = create("<= ?", arg)

    def is(exp: String, args: Any*): Expression = {
      val argCount = Strings.count(exp, '?')
      require(argCount == args.size, s"$exp has $argCount args,but given ${args.size}.")
      create(exp, args: _*)
    }

    def between(start: Any, end: Any): Expression = {
      create("between ? and ?", start, end)
    }

    private def create(con: String, args: Any*): Expression = {
      val exp = if con.contains("_") then Strings.replace(con, "_", name) else name + " " + con
      new Expression(exp, args)
    }

    def fillin(alias: String): String = {
      Strings.replace(this.name, "_.", alias + ".")
    }

    def f(func: String): Var = {
      Var(Strings.replace(func, "_", this.name))
    }
  }

  class Expression(val exp: String, val args: Seq[Any]) {

    def and(o: Expression): Expression = {
      new Expression(this.safeExp + " and " + o.safeExp, this.args ++ o.args)
    }

    def or(o: Expression): Expression = {
      new Expression(s"${this.exp} or ${o.exp}", this.args ++ o.args)
    }

    def append(query: OqlBuilder[_]): Unit = {
      var clause = Strings.replace(exp, "_.", query.alias + ".")
      val s = query.params.size
      args.indices foreach { i =>
        val idx = clause.indexOf('?')
        if (idx != -1) {
          clause = clause.substring(0, idx) + s":v${s + i + 1}" + (if (idx == clause.length - 1) "" else clause.substring(idx + 1))
        }
      }
      val con = new Condition(clause).params(args)
      query.where(con)
    }

    private def safeExp: String = {
      if this.exp.charAt(0) != '(' && this.exp.contains(" or ") then s"(${this.exp})" else this.exp
    }

  }

}

/**
 * 实体类查询 Object Query Language Builder
 *
 * @author chaostone
 */
class OqlBuilder[T] private() extends AbstractQueryBuilder[T] {

  /** 查询实体类 */
  var entityClass: Class[T] = _
  var tracker: AccessTracker & T = _

  /**
   * 形成计数查询语句，如果不能形成，则返回""
   */
  protected def genCountStatement(): String = {
    val countString = new StringBuilder("select count(*) ")
    // 原始查询语句
    val genQueryStr = genQueryStatement(false)
    if (isEmpty(genQueryStr)) {
      return ""
    }
    val lowerCaseQueryStr = genQueryStr.toLowerCase()

    if (contains(lowerCaseQueryStr, " group ")) {
      return ""
    }
    if (contains(lowerCaseQueryStr, " union ")) {
      return ""
    }

    val indexOfFrom = findIndexOfFrom(lowerCaseQueryStr)
    val selectWhat = lowerCaseQueryStr.substring(0, indexOfFrom)
    val indexOfDistinct = selectWhat.indexOf("distinct")
    // select distinct a from table
    if (-1 != indexOfDistinct) {
      if (contains(selectWhat, ",")) {
        return ""
      } else {
        countString.clear()
        countString.append("select count(")
        countString.append(genQueryStr.substring(indexOfDistinct, indexOfFrom)).append(") ")
      }
    }

    var orderIdx = genQueryStr.lastIndexOf(" order ")
    if (-1 == orderIdx) orderIdx = genQueryStr.length()
    countString.append(genQueryStr.substring(indexOfFrom, orderIdx))
    countString.toString()
  }

  /** Find index of from
   *
   * @param query query string
   * @return -1 or from index
   */
  private def findIndexOfFrom(query: String): Int = {
    if (query.startsWith("from")) return 0
    var fromIdx = query.indexOf(" from ")
    if (-1 == fromIdx) return -1
    val first = query.substring(0, fromIdx).indexOf("(")
    if (first > 0) {
      var leftCnt = 1
      var i = first + 1
      while (leftCnt != 0 && i < query.length) {
        if (query.charAt(i) == '(') leftCnt += 1
        else if (query.charAt(i) == ')') leftCnt -= 1
        i += 1
      }
      if (leftCnt > 0) {
        -1
      } else {
        fromIdx = query.indexOf(" from ", i)
        if (fromIdx == -1) -1 else fromIdx + 1
      }
    } else {
      fromIdx + 1
    }
  }

  def forEntity(entityClass: Class[T]): this.type = {
    this.entityClass = entityClass
    this.tracker = AccessTracker.of(entityClass)
    this
  }

  def where(exp: T => Expression): this.type = {
    exp(tracker).append(this)
    this
  }

  def on(exp: T => Any): this.type = {
    exp(tracker)
    this
  }

  def groupBy(vars: Var*): this.type = {
    val clause = vars.map(e => e.fillin(this.alias)).mkString(",")
    if clause.nonEmpty then groupBy(clause)
    this
  }

  def select(vars: Var*): this.type = {
    val clause = vars.map(e => e.fillin(this.alias)).mkString(",")
    if clause.nonEmpty then select(clause)
    this
  }

  def orderBy(vars: Var*): this.type = {
    val clause = vars.map(e => e.fillin(this.alias)).mkString(",")
    if clause.nonEmpty then orderBy(clause)
    this
  }

  override def lang: Query.Lang = Query.Lang.OQL

  import scala.language.implicitConversions

  implicit def any2Var(a: Any): Var = {
    val props = tracker.ctx.accessed()
    if props.isEmpty then new Var(a.toString) else new Var("_." + props.head)
  }
}
