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

import org.beangle.commons.collection.page.{Page, PageLimit, SinglePage}
import org.beangle.commons.lang.Strings
import org.beangle.commons.lang.annotation.description
import org.beangle.commons.logging.Logging
import org.beangle.data.dao.{EntityDao, LimitQuery, Operation, OperationType, OqlBuilder, QueryBuilder, Query as BQuery}
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.Domain
import org.beangle.data.orm.Jpas
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.{SessionFactoryImplementor, SessionImplementor}
import org.hibernate.proxy.HibernateProxy
import org.hibernate.query.{NativeQuery, Query}
import org.hibernate.{Hibernate, Session, SessionFactory}

import java.io.{ByteArrayOutputStream, InputStream, Serializable}
import java.sql.{Blob, Clob}
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.asScala


/**
 * @author chaostone
 */
@description("基于Hibernate提供的通用实体DAO")
class HibernateEntityDao(val sessionFactory: SessionFactory) extends EntityDao with Logging {
  val domain: Domain = DomainFactory.build(sessionFactory)

  Jpas.proxyResolver = HibernateProxyResolver

  import QuerySupport.*

  protected def currentSession: Session = {
    sessionFactory.getCurrentSession
  }

  private def createQuery(hql: String): Query[_] = {
    currentSession.createQuery(hql, null).asInstanceOf[Query[_]]
  }

  protected def entityNameOf(clazz: Class[_]): String = {
    domain.getEntity(clazz) match {
      case Some(e) => e.entityName
      case None => clazz.getName
    }
  }

  override def get[T <: Entity[ID], ID](clazz: Class[T], id: ID): T = {
    find(entityNameOf(clazz), id).orNull.asInstanceOf[T]
  }

  override def getAll[T](clazz: Class[T]): Seq[T] = {
    val hql = "from " + entityNameOf(clazz)
    val query = createQuery(hql)
    query.setCacheable(true)
    asScala(query.list()).toList.asInstanceOf[List[T]]
  }

  def find[T <: Entity[ID], ID](entityName: String, id: ID): Option[T] = {
    if (Strings.contains(entityName, '.')) {
      val obj = currentSession.get(entityName, id.asInstanceOf[Serializable])
      if (null == obj) None else Some(obj.asInstanceOf[T])
    } else {
      val hql = "from " + entityName + " where id =:id"
      val query = createQuery(hql)
      query.setParameter("id", id)
      val rs = query.list()
      if (rs.isEmpty) None else Some(rs.get(0).asInstanceOf[T])
    }
  }

  override def find[T <: Entity[ID], ID](clazz: Class[T], id: ID): Option[T] = {
    find[T, ID](entityNameOf(clazz), id)
  }

  override def find[T <: Entity[ID], ID](clazz: Class[T], ids: Iterable[ID]): Seq[T] = {
    findByMulti(entityNameOf(clazz), "id", ids)
  }

  override def findBy[T <: Entity[_]](clazz: Class[T], key: String, value: Any): Seq[T] = {
    findBy(clazz, Map(key -> value))
  }

  override def findBy[T <: Entity[_]](clazz: Class[T], kvs: (String, Any)*): Seq[T] = {
    findBy(clazz, kvs.toMap)
  }

  override def findBy[T <: Entity[_]](clazz: Class[T], params: collection.Map[String, _]): Seq[T] = {
    if (clazz == null || params == null || params.isEmpty) return List.empty
    val entityName = entityNameOf(clazz)
    val hql = new StringBuilder(s"from $entityName where ")
    val where = buildWhere(params)
    hql.append(where._1)
    search[T](hql.toString(), where._2)
  }

  private def findByMulti[T <: Entity[_]](entityName: String, keyName: String, values: Iterable[_]): Seq[T] = {
    if (values.isEmpty) return List.empty
    val hql = s"from $entityName as entity where entity.$keyName in (:values)"
    val parameterMap = new mutable.HashMap[String, Any]
    if (values.size < 500) {
      parameterMap.put("values", values)
      val query = OqlBuilder.oql(hql)
      search(query.params(parameterMap).build())
    } else {
      val query = OqlBuilder.oql(hql)
      val rs = new mutable.ListBuffer[T]
      var i = 0
      while (i < values.size) {
        var end = i + 500
        if (end > values.size) end = values.size
        parameterMap.put("values", values.slice(i, end))
        rs ++= search(query.params(parameterMap).build())
        i += 500
      }
      rs.toList
    }
  }

  private def buildWhere(params: collection.Map[String, _], prefix: String = ""): (String, collection.Map[String, Any]) = {
    val parameterMap = new mutable.HashMap[String, Any]
    val conditions = new mutable.ArrayBuffer[String]
    var i = 0
    params foreach { case (k, v) =>
      i += 1
      val tempName = Strings.split(k, "\\.")
      val name = tempName(tempName.length - 1) + i
      if QuerySupport.isMultiValue(v) then
        parameterMap.put(name, v)
        conditions += s"$prefix$k in (:$name)"
      else if v == null || v == None then
        conditions += s"$prefix$k is null"
      else
        parameterMap.put(name, v)
        conditions += s"$prefix$k = :$name"
      end if
    }
    (conditions.mkString(" and "), parameterMap)
  }

  override def count(clazz: Class[_], kvs: (String, Any)*): Int = {
    count(clazz, kvs.toMap)
  }

  override def count(clazz: Class[_], params: collection.Map[String, _]): Int = {
    val entityName = entityNameOf(clazz)
    val where = buildWhere(params)
    val hql =
      if Strings.isEmpty(where._1) then s"select count(*) from $entityName"
      else s"select count(*) from $entityName where " + where._1

    val rs = search[Number](hql, where._2)
    if (rs.isEmpty) 0 else rs.head.intValue()
  }

  override def exists(clazz: Class[_], kvs: (String, Any)*): Boolean = {
    count(clazz, kvs.toMap) > 0
  }

  override def exists(clazz: Class[_], params: collection.Map[String, _]): Boolean = {
    count(clazz, params) > 0
  }

  override def duplicate[T <: Entity[_]](clazz: Class[T], id: Any, params: collection.Map[String, _]): Boolean = {
    val entityName = entityNameOf(clazz)
    val where = buildWhere(params)
    val hql = s"from $entityName where " + where._1
    val list = search[T](hql, where._2)
    if (list.isEmpty) {
      false
    } else {
      if id == null then true else list.exists(e => e.id != id)
    }
  }

  /**
   * 依据自构造的查询语句进行查询
   */
  override def search[T](query: BQuery[T]): Seq[T] = {
    query match {
      case limitQuery: LimitQuery[T] =>
        if (null == limitQuery.limit) {
          doFind(limitQuery, currentSession)
        } else {
          new SinglePage[T](limitQuery.limit.pageIndex, limitQuery.limit.pageSize,
            doCount(limitQuery, currentSession), doFind(query, currentSession))
        }
      case _ =>
        doFind(query, currentSession)
    }
  }

  override def search[T](builder: QueryBuilder[T]): Seq[T] = {
    search[T](builder.build())
  }

  override def search[T](query: String, params: Any*): Seq[T] = {
    list[T](setParameters(getNamedOrCreateQuery[T](query), params))
  }

  override def search[T](queryString: String, params: collection.Map[String, _]): Seq[T] = {
    list[T](setParameters(getNamedOrCreateQuery[T](queryString), params))
  }

  override def search[T](queryString: String, params: collection.Map[String, _], limit: PageLimit, cacheable: Boolean): Seq[T] = {
    val query = getNamedOrCreateQuery[T](queryString)
    query.setCacheable(cacheable)
    if (null == limit) list(setParameters(query, params))
    else paginateQuery(query, params, limit)
  }

  override def unique[T](builder: QueryBuilder[T]): T = {
    val list = search(builder.build())
    if (list.isEmpty) {
      null.asInstanceOf[T]
    } else if (list.size == 1) {
      list.head
    } else {
      throw new RuntimeException("not unique query" + builder)
    }
  }

  override def first[T](builder: QueryBuilder[T]): Option[T] = {
    topN(1, builder).headOption
  }

  override def topN[T](limit: Int, builder: QueryBuilder[T]): Seq[T] = {
    builder.limit(PageLimit(1, limit))
    doFind(builder.build(), currentSession)
  }

  override def topN[T](limit: Int, queryString: String, params: Any*): Seq[T] = {
    val query = setParameters(getNamedOrCreateQuery[T](queryString), params)
    list(query.setMaxResults(limit))
  }

  private def paginateQuery[T](query: Query[T], params: collection.Map[String, _], limit: PageLimit): Page[T] = {
    setParameters(query, params)
    query.setFirstResult((limit.pageIndex - 1) * limit.pageSize).setMaxResults(limit.pageSize)
    val targetList = query.list()
    val queryStr = buildCountQueryStr(query)
    var countQuery: Query[_] = null
    if (query.isInstanceOf[NativeQuery[_]]) {
      countQuery = currentSession.createNativeQuery(queryStr).asInstanceOf[Query[T]]
    } else {
      countQuery = createQuery(queryStr)
    }
    setParameters(countQuery, params)
    // 返回结果
    new SinglePage[T](limit.pageIndex, limit.pageSize, countQuery.uniqueResult().asInstanceOf[Number].intValue, asScala(targetList))
  }

  override def evict(entity: Entity[_]): Unit = {
    val clazz = entity match {
      case hp: HibernateProxy => hp.getHibernateLazyInitializer.getPersistentClass
      case _ => entity.getClass
    }
    sessionFactory.getCache.evict(clazz, entity.id)
  }

  override def evict[A <: Entity[_]](clazz: Class[A]): Unit = {
    val sf = sessionFactory.asInstanceOf[SessionFactoryImplementor]
    //1. evict entity cache
    sf.getCache.evict(clazz)

    //2. evict query cache according to entity table
    val entityName = entityNameOf(clazz)
    val entityDescriptor = sf.getRuntimeMetamodels.getMappingMetamodel.getEntityDescriptor(entityName)
    sf.getCache.getTimestampsCache.invalidate(entityDescriptor.getPropertySpaces,
      this.currentSession.asInstanceOf[SessionImplementor])
  }

  override def refresh[T](entity: T): T = {
    currentSession.refresh(entity)
    entity
  }

  override def initialize[T](proxy: T): T = {
    var rs = proxy
    proxy match {
      case hp: HibernateProxy =>
        val initer = hp.getHibernateLazyInitializer
        if (null == initer.getSession || initer.getSession.isClosed) {
          rs = currentSession.get(initer.getEntityName, initer.getIdentifier).asInstanceOf[T]
        } else {
          Hibernate.initialize(proxy)
        }
      case pc: PersistentCollection[_] => Hibernate.initialize(pc)
    }
    rs
  }

  override def remove[E](entities: Iterable[E]): Unit = {
    if (null == entities || entities.isEmpty) return
    val session = currentSession
    for (entity <- entities; if null != entity)
      entity match {
        case seq: Iterable[_] => seq.foreach(session.remove)
        case _ => session.remove(entity)
      }
  }

  override def remove[E](first: E, entities: E*): Unit = {
    remove(first :: entities.toList)
  }

  override def remove[T <: Entity[ID], ID](clazz: Class[T], id: ID, ids: ID*): Unit = {
    val idList: Iterable[_] = id :: ids.toList
    removeBy(clazz, Map("id" -> idList))
  }

  override def removeBy(clazz: Class[_], params: collection.Map[String, _]): Int = {
    if (clazz == null || params == null || params.isEmpty) return 0
    val where = buildWhere(params)
    val hql = s"delete from ${entityNameOf(clazz)} where " + where._1
    executeUpdate(hql, where._2)
  }

  override def executeUpdate(queryString: String, parameterMap: collection.Map[String, _]): Int = {
    setParameters(getNamedOrCreateQuery[Any](queryString), parameterMap).executeUpdate()
  }

  override def executeUpdate(queryString: String, arguments: Any*): Int = {
    setParameters(getNamedOrCreateQuery[Any](queryString), arguments).executeUpdate()
  }

  override def executeUpdateRepeatly(queryString: String, arguments: Iterable[Iterable[_]]): List[Int] = {
    val query = getNamedOrCreateQuery[Any](queryString)
    val updates = new mutable.ListBuffer[Int]
    for (params <- arguments) {
      updates += setParameters(query, params).executeUpdate()
    }
    updates.toList
  }

  override def execute(opts: Operation*): Unit = {
    for (operation <- opts) {
      operation.typ match {
        case OperationType.SaveUpdate => persistEntity(operation.data, null)
        case OperationType.Remove => remove(operation.data)
      }
    }
  }

  override def execute(builder: Operation.Builder): Unit = {
    for (operation <- builder.build()) {
      operation.typ match {
        case OperationType.SaveUpdate => persistEntity(operation.data, null)
        case OperationType.Remove => remove(operation.data)
      }
    }
  }

  override def saveOrUpdate[E](first: E, entities: E*): Unit = {
    saveOrUpdate(first :: entities.toList)
  }

  override def saveOrUpdate[E](entities: Iterable[E]): Unit = {
    if (entities.nonEmpty) {
      for (entity <- entities)
        entity match {
          case col: IterableOnce[_] => col.iterator.foreach { e => persistEntity(e, null) }
          case _ => persistEntity(entity, null)
        }
    }
  }

  /**
   * Persist entity using save or update,UPDATE entity should load in session first.
   */
  private def persistEntity(entity: Any, entityName: String): Unit = {
    if (null == entity) return
    val session = currentSession
    entity match {
      case hp: HibernateProxy => session.update(hp)
      case e: Entity[_] =>
        val en = if (null == entityName) entityNameOf(entity.getClass) else entityName
        if (null == e.id) {
          session.persist(en, entity)
        } else {
          val si = session.asInstanceOf[SessionImplementor]
          if (si.getContextEntityIdentifier(entity) == null) {
            session.persist(en, entity)
          } else {
            session.update(en, entity)
          }
        }
      case _ =>
        val en = if (null == entityName) entityNameOf(entity.getClass) else entityName
        session.persist(en, entity)
    }
  }

  // update entityClass set [argumentName=argumentValue,]* where attr in values
  def batchUpdate(entityClass: Class[_], attr: String, values: Iterable[_], argumentNames: Iterable[String], argumentValues: Iterable[Any]): Int = {
    if (values.isEmpty) return 0
    val updateParams = new mutable.HashMap[String, Any]
    val valueIter = argumentValues.iterator
    argumentNames.foreach { n =>
      updateParams.put(n, valueIter.next())
    }
    batchUpdate(entityClass, attr, values, updateParams)
  }

  def batchUpdate(entityClass: Class[_], attr: String, values: Iterable[_], updateParams: scala.collection.Map[String, _]): Int = {
    if (values.isEmpty || updateParams.isEmpty) return 0
    val hql = new StringBuilder()
    hql.append("update ").append(entityNameOf(entityClass)).append(" set ")
    val newParams = new mutable.HashMap[String, Any]
    for ((parameterName, value) <- updateParams; if null != parameterName) {
      val locateParamName = Strings.replace(parameterName, ".", "_")
      hql.append(parameterName).append(" = ").append(":").append(locateParamName).append(",")
      newParams.put(locateParamName, value)
    }
    hql.deleteCharAt(hql.length() - 1)
    hql.append(" where ").append(attr).append(" in (:ids)")
    newParams.put("ids", values)
    executeUpdate(hql.toString(), newParams)
  }

  override def createBlob(inputStream: InputStream, length: Int): Blob = {
    currentSession.getLobHelper.createBlob(inputStream, length)
  }

  override def createBlob(inputStream: InputStream): Blob = {
    val buffer = new ByteArrayOutputStream(inputStream.available())
    currentSession.getLobHelper.createBlob(buffer.toByteArray)
  }

  override def createClob(str: String): Clob = {
    currentSession.getLobHelper.createClob(str)
  }

  /**
   * Support "@named-query" or "from object" styles query
   */
  private def getNamedOrCreateQuery[T](queryString: String): Query[T] = {
    if (queryString.charAt(0) == '@') currentSession.createNamedQuery(queryString.substring(1), null).asInstanceOf[Query[T]]
    else currentSession.createQuery(queryString, null).asInstanceOf[Query[T]]
  }

  /**
   * 构造查询记录数目的查询字符串
   */
  private def buildCountQueryStr(query: Query[_]): String = {
    var queryStr = "select count(*) "
    if (query.isInstanceOf[NativeQuery[_]]) {
      queryStr += ("from (" + query.getQueryString + ")")
    } else {
      val lowerCaseQueryStr = query.getQueryString.toLowerCase()
      val selectWhich = lowerCaseQueryStr.substring(0, query.getQueryString.indexOf("from"))
      val indexOfDistinct = selectWhich.indexOf("distinct")
      val indexOfFrom = lowerCaseQueryStr.indexOf("from")
      // 如果含有distinct
      if (-1 != indexOfDistinct) {
        if (Strings.contains(selectWhich, ",")) {
          queryStr = "select count(" + query.getQueryString.substring(indexOfDistinct, query.getQueryString.indexOf(",")) + ")"
        } else {
          queryStr = "select count(" + query.getQueryString.substring(indexOfDistinct, indexOfFrom) + ")"
        }
      }
      queryStr += query.getQueryString.substring(indexOfFrom)
    }
    queryStr
  }

}
