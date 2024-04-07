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

package org.beangle.data.orm.hibernate

import org.beangle.commons.collection.Wrappers
import org.beangle.commons.lang.Strings
import org.beangle.data.dao.{Condition, LimitQuery, Query as BQuery}
import org.hibernate.Session
import org.hibernate.query.Query

import scala.jdk.javaapi.CollectionConverters.asJava

object QuerySupport {

  def list[T](query: Query[T]): Seq[T] = {
    Wrappers.ImmutableJList(query.list())
  }

  private def buildHibernateQuery[T](bquery: BQuery[T], session: Session): Query[T] = {
    val query =
      if (bquery.lang == BQuery.Lang.SQL) {
        //FIXME native query cannot enable cache
        session.createNativeQuery(bquery.statement).asInstanceOf[Query[T]]
      } else {
        val q = session.createQuery(bquery.statement, null).asInstanceOf[Query[T]]
        if (bquery.cacheable) q.setCacheable(bquery.cacheable)
        q
      }
    setParameters(query, bquery.params)
  }

  /**
   * 统计该查询的记录数
   */
  def doCount(limitQuery: LimitQuery[_], hibernateSession: Session): Int = {
    val cntQuery = limitQuery.countQuery
    if (null == cntQuery) {
      buildHibernateQuery(limitQuery, hibernateSession).list().size()
    } else {
      val count = buildHibernateQuery(cntQuery, hibernateSession).uniqueResult().asInstanceOf[Number]
      if (null == count) 0 else count.intValue()
    }
  }

  /**
   * 查询结果集
   */
  def doFind[T](query: BQuery[T], session: Session): Seq[T] = {
    val hQuery = query match {
      case limitQuery: LimitQuery[_] =>
        val hibernateQuery = buildHibernateQuery(limitQuery, session)
        if (null != limitQuery.limit) {
          val limit = limitQuery.limit
          hibernateQuery.setFirstResult((limit.pageIndex - 1) * limit.pageSize).setMaxResults(limit.pageSize)
        }
        hibernateQuery
      case _ => buildHibernateQuery(query, session)
    }
    list[T](hQuery)
  }

  /**
   * 为query设置JPA style参数
   */
  def setParameters[T](query: Query[T], argument: Iterable[_]): Query[T] = {
    if (argument != null && argument.nonEmpty) {
      var i = 1
      val iter = argument.iterator
      while (iter.hasNext) {
        setParameter(query, i, iter.next().asInstanceOf[AnyRef])
        i += 1
      }
    }
    query
  }

  /**
   * 为query设置参数
   */
  def setParameters[T](query: Query[T], parameterMap: collection.Map[String, _]): Query[T] = {
    if (parameterMap != null && parameterMap.nonEmpty) {
      for ((k, v) <- parameterMap; if null != k) setParameter(query, k, v)
    }
    query
  }

  def setParameter[T](query: Query[T], idx: Int, value: Any): Query[T] = {
    value match {
      case null => query.setParameter(idx, null.asInstanceOf[AnyRef])
      case av: Array[AnyRef] => query.setParameterList(idx, av)
      case col: java.util.Collection[_] => query.setParameterList(idx, col)
      case iter: Iterable[_] => query.setParameterList(idx, asJava(iter.toList))
      case _ => query.setParameter(idx, value)
    }
    query
  }

  def setParameter[T](query: Query[T], param: String, value: Any): Query[T] = {
    value match {
      case null => query.setParameter(param, null.asInstanceOf[AnyRef])
      case av: Array[AnyRef] => query.setParameterList(param, av)
      case col: java.util.Collection[_] => query.setParameterList(param, col)
      case Some(v) => setParameter(query, param, v)
      case None => query.setParameter(param, null.asInstanceOf[AnyRef])
      case iter: Iterable[_] => query.setParameterList(param, asJava(iter.toList))
      case _ => query.setParameter(param, value)
    }
    query
  }

  def isMultiValue(value: Any): Boolean = {
    value match {
      case null => false
      case av: Array[AnyRef] => true
      case col: java.util.Collection[_] => true
      case iter: Iterable[_] => true
      case _ => false
    }
  }

  /**
   * 针对查询条件绑定查询的值
   */
  def bindValues(query: Query[_], conditions: List[Condition]): Unit = {
    var position = 0
    var hasInterrogation = false // 含有问号
    for (condition <- conditions) {
      if (Strings.contains(condition.content, "?")) hasInterrogation = true
      if (hasInterrogation) {
        for (o <- condition.params) {
          query.setParameter(position, o)
          position += 1
        }
      } else {
        val paramNames = condition.paramNames
        for (i <- 0 until paramNames.size)
          setParameter(query, paramNames(i), condition.params.apply(i))
      }
    }
  }
}
