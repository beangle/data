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

import org.beangle.commons.collection.Collections
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
    query.proxy = AccessProxy.of(query.entityClass)
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
    query.proxy = AccessProxy.of(entityClass)
    query.alias = alias
    query.select = "select " + alias
    query.from = concat("from ", Jpas.findEntityName(entityClass), " ", alias)
    query
  }

  class Var(proxy: AccessProxy) {

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
      require(argCount == args.size, s"${exp} has ${argCount} args,but given ${args.size}.")
      create(exp, args: _*)
    }

    def between(start: Any, end: Any): Expression = {
      create("between ? and ?", start, end)
    }

    private def create(con: String, args: Any*): Expression = {
      val exp = proxy.ctx.accessed().map { p =>
        if con.contains("$") then Strings.replace(con, "$", "$alias" + "." + p) else "$alias" + "." + p + " " + con
      }.mkString(" and ")
      new Expression(proxy, exp, args)
    }
  }

  class Expression(proxy: AccessProxy) {
    protected var exp: String = ""
    protected val args = Collections.newBuffer[Any]

    def this(proxy: AccessProxy, exp: String) = {
      this(proxy)
      this.exp = exp
    }

    def this(proxy: AccessProxy, exp: String, args: Iterable[Any]) = {
      this(proxy)
      this.exp = exp
      this.args.addAll(args)
    }

    def and(o: Expression): Expression = {
      new Expression(this.proxy, this.exp + " and " + o.exp, this.args.addAll(o.args))
    }

    def or(o: Expression): Expression = {
      new Expression(this.proxy, this.exp + " or " + o.exp, this.args.addAll(o.args))
    }

    def append(query: OqlBuilder[_]): Unit = {
      var clause = Strings.replace(exp, "$alias", query.alias)
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
  var proxy: AccessProxy & T = _

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
    this.proxy = AccessProxy.of(entityClass)
    this
  }

  def where(exp: T => Expression): this.type = {
    exp(proxy).append(this)
    this
  }

  override def lang: Query.Lang = Query.Lang.OQL

  import scala.language.implicitConversions
  implicit def any2Var(a: Any): Var = new Var(proxy)
}
