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

import org.beangle.commons.collection.page.PageLimit
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.Domain

import java.io.InputStream
import java.sql.{Blob, Clob}
import scala.collection.immutable.Seq

/**
 * dao 查询辅助类
 *
 * @author chaostone
 */
trait EntityDao {
  /** 查询指定id的对象
   *
   * @param clazz 类型
   * @param id    唯一标识
   */
  def get[T <: Entity[ID], ID](clazz: Class[T], id: ID): T

  def getAll[T <: Entity[_]](clazz: Class[T]): Seq[T]

  /** find T by id.
   */
  def find[T <: Entity[ID], ID](clazz: Class[T], id: ID): Option[T]

  def find[T <: Entity[ID], ID](clazz: Class[T], ids: Iterable[ID]): Seq[T]

  def findBy[T <: Entity[_]](clazz: Class[T], key: String, value: Any): Seq[T]

  def findBy[T <: Entity[_]](clazz: Class[T], kv: (String, Any)*): Seq[T]

  def findBy[T <: Entity[_]](clazz: Class[T], params: collection.Map[String, _]): Seq[T]

  /**
   * save or update entities
   */
  def saveOrUpdate[E](first: E, entities: E*): Unit

  /**
   * save or update entities
   */
  def saveOrUpdate[E](entities: Iterable[E]): Unit

  /**
   * remove entities.
   */
  def remove[E](entities: Iterable[E]): Unit

  /**
   * remove entities.
   */
  def remove[E](first: E, entities: E*): Unit

  /**
   * remove entities by id
   */
  def remove[T <: Entity[ID], ID](clazz: Class[T], id: ID, ids: ID*): Unit

  /** Remove entities by params
   *
   * @param clazz
   * @param params
   * @return
   */
  def removeBy(clazz: Class[_], params: collection.Map[String, _]): Int

  /**
   * Search by QueryBuilder
   */
  def search[T](builder: QueryBuilder[T]): Seq[T]

  /**
   * Search Query
   */
  def search[T](query: Query[T]): Seq[T]

  def search[T](query: String, params: Any*): Seq[T]

  def search[T](queryString: String, params: collection.Map[String, _]): Seq[T]

  def search[T](queryString: String, params: collection.Map[String, _], limit: PageLimit, cacheable: Boolean): Seq[T]

  /** Get Top N entities
   *
   * @param builder
   * @tparam T
   * @return
   */
  def topN[T](limit: Int, builder: QueryBuilder[T]): Seq[T]

  /** Get Top N entities
   *
   * @param limit
   * @param queryString
   * @param params
   * @tparam T
   * @return
   */
  def topN[T](limit: Int, queryString: String, params: Any*): Seq[T]

  /** Search Unique Result
   */
  def unique[T](builder: QueryBuilder[T]): T

  /** Get first
   * */
  def first[T](builder: QueryBuilder[T]): Option[T]

  /** 在同一个session保存、删除
   */
  def execute(opts: Operation*): Unit

  /** 执行一个操作构建者提供的一系列操作
   *
   * @param builder 操作构建者
   */
  def execute(builder: Operation.Builder): Unit

  def executeUpdate(queryString: String, parameterMap: collection.Map[String, _]): Int

  def executeUpdate(queryString: String, arguments: Any*): Int

  def executeUpdateRepeatly(queryString: String, arguments: Iterable[Iterable[_]]): List[Int]

  // 容器相关
  def evict(entity: Entity[_]): Unit

  def evict[A <: Entity[_]](clazz: Class[A]): Unit

  /**
   * Initialize entity whenever session close or open
   */
  def initialize[T](entity: T): T

  def refresh[T](entity: T): T

  def count(clazz: Class[_], kvs: Tuple2[String, Any]*): Int

  def count(clazz: Class[_], params: collection.Map[String, _]): Int

  def exists(clazz: Class[_], kv: Tuple2[String, Any]*): Boolean

  def exists(clazz: Class[_], params: collection.Map[String, _]): Boolean

  def duplicate[T <: Entity[_]](clazz: Class[T], id: Any, params: collection.Map[String, _]): Boolean

  def createBlob(inputStream: InputStream, length: Int): Blob

  def createBlob(inputStream: InputStream): Blob

  def createClob(str: String): Clob

  def domain: Domain
}
