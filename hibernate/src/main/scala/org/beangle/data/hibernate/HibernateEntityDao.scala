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
package org.beangle.data.hibernate

import java.io.{ByteArrayOutputStream, InputStream, Serializable}
import java.sql.{Blob, Clob}

import org.beangle.commons.collection.Wrappers
import org.beangle.commons.collection.page.{Page, PageLimit, SinglePage}
import org.beangle.commons.lang.annotation.description
import org.beangle.commons.lang.{Assert, Strings}
import org.beangle.commons.logging.Logging
import org.beangle.data.dao.{Condition, EntityDao, LimitQuery, Operation, OqlBuilder, QueryBuilder, Query => BQuery}
import org.beangle.data.model.Entity
import org.beangle.data.model.meta.Domain
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.jdbc.StreamUtils
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.proxy.HibernateProxy
import org.hibernate.query.{NativeQuery, Query}
import org.hibernate.{Hibernate, Session, SessionFactory}

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.jdk.javaapi.CollectionConverters.{asJava, asScala}

object QuerySupport {

  def list[T](query: Query[T]): Seq[T] = {
    Wrappers.ImmutableJList(query.list())
  }

  private def buildHibernateQuery[T](bquery: BQuery[T], session: Session): Query[T] = {
    val query =
      if (bquery.lang == BQuery.SQL) {
        //FIXME native query cannot enable cache
        session.createNativeQuery(bquery.statement).asInstanceOf[Query[T]]
      } else {
        val q = session.createQuery(bquery.statement).asInstanceOf[Query[T]]
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
        query.setParameter(String.valueOf(i), iter.next().asInstanceOf[AnyRef])
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

  def setParameter[T](query: Query[T], param: String, value: Any): Query[T] = {
    value match {
      case null => query.setParameter(param, null.asInstanceOf[AnyRef])
      case av: Array[AnyRef] => query.setParameterList(param, av)
      case col: java.util.Collection[_] => query.setParameterList(param, col)
      case iter: Iterable[_] => query.setParameterList(param, asJava(iter.toList))
      case _ => query.setParameter(param, value)
    }
    query
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

/**
  * @author chaostone
  */
@description("基于Hibernate提供的通用实体DAO")
class HibernateEntityDao(val sessionFactory: SessionFactory) extends EntityDao with Logging {
  val domain: Domain = DomainFactory.build(sessionFactory)

  import QuerySupport._

  protected def currentSession: Session = {
    sessionFactory.getCurrentSession
  }

  private def createQuery(hql: String): Query[_] = {
    currentSession.createQuery(hql).asInstanceOf[Query[_]]
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
    findBy(entityNameOf(clazz), "id", ids)
  }

  override def findBy[T <: Entity[_]](clazz: Class[T], keyName: String, values: Iterable[_]): Seq[T] = {
    findBy(entityNameOf(clazz), keyName, values)
  }

  override def findBy[T <: Entity[_]](entityName: String, keyName: String, values: Iterable[_]): Seq[T] = {
    if (values.isEmpty) return List.empty
    val hql = new StringBuilder()
    hql.append("select entity from ").append(entityName).append(" as entity where entity.").append(keyName)
      .append(" in (:keyName)")
    val parameterMap = new mutable.HashMap[String, Any]
    if (values.size < 500) {
      parameterMap.put("keyName", values)
      val query = OqlBuilder.oql(hql.toString())
      search(query.params(parameterMap).build())
    } else {
      val query = OqlBuilder.oql(hql.toString())
      val rs = new mutable.ListBuffer[T]
      var i = 0
      while (i < values.size) {
        var end = i + 500
        if (end > values.size) end = values.size
        parameterMap.put("keyName", values.slice(i, end))
        rs ++= search(query.params(parameterMap).build())
        i += 500
      }
      rs.toList
    }
  }

  def find[T <: Entity[_]](clazz: Class[T], parameterMap: collection.Map[String, _]): Seq[T] = {
    if (clazz == null || parameterMap == null || parameterMap.isEmpty) {
      return List.empty
    }
    val hql = new StringBuilder()
    hql.append("select entity from ").append(entityNameOf(clazz)).append(" as entity ").append(" where ")

    val m = new mutable.HashMap[String, Any]
    // 变量编号
    var i = 0
    for ((keyName, keyValue) <- parameterMap; if Strings.isNotEmpty(keyName)) {
      i += 1

      val tempName = Strings.split(keyName, "\\.")
      val name = tempName(tempName.length - 1) + i
      m.put(name, keyValue)

      if (keyValue != null && isCollectionType(keyValue.getClass)) {
        hql.append("entity.").append(keyName).append(" in (:").append(name).append(") and ")
      } else {
        hql.append("entity.").append(keyName).append(" = :").append(name).append(" and ")
      }
    }
    if (i > 0) hql.delete(hql.length() - " and ".length(), hql.length())
    search(hql.toString, m)
  }

  def count(entityName: String, keyName: String, value: Any): Long = {
    val hql = "select count(*) from " + entityName + " where " + keyName + "=:value"
    val rs = search(hql, Map("value" -> value))
    if (rs.isEmpty) 0 else rs.head.asInstanceOf[Number].longValue
  }

  override def count(entityClass: Class[_], keyName: String, value: Any): Long = {
    count(entityClass.getName, keyName, value)
  }

  def count(entityClass: Class[_], attrs: Iterable[String], values: Iterable[_], countAttr: String): Long = {
    Assert.isTrue(null != attrs && null != values && attrs.size == values.size)

    val entityName = entityClass.getName
    val hql = new StringBuilder()
    if (Strings.isNotEmpty(countAttr)) {
      hql.append("select count(distinct ").append(countAttr).append(") from ")
    } else {
      hql.append("select count(*) from ")
    }
    hql.append(entityName).append(" as entity where ")
    val params = new mutable.HashMap[String, Any]
    val attrIter = attrs.iterator
    val valueIter = values.iterator
    for (i <- 0 until attrs.size) {
      val attr = attrIter.next()
      if (Strings.isNotEmpty(attr)) {
        val keyName = Strings.replace(attr, ".", "_")
        val keyValue = valueIter.next()
        params += (keyName -> keyValue)
        if (keyValue != null && isCollectionType(keyValue.getClass)) {
          hql.append("entity.").append(attr).append(" in (:").append(keyName).append(')')
        } else {
          hql.append("entity.").append(attr).append(" = :").append(keyName)
        }
        if (i < attrs.size - 1) hql.append(" and ")
      }
    }
    search(hql.toString, params).head.asInstanceOf[Number].longValue
  }

  override def exists(entityClass: Class[_], attr: String, value: Any): Boolean = {
    count(entityClass, attr, value) > 0
  }

  override def exists(entityName: String, attr: String, value: Any): Boolean = {
    count(entityName, attr, value) > 0
  }

  def exists(entityClass: Class[_], attrs: Iterable[String], values: Iterable[_]): Boolean = {
    count(entityClass, attrs, values, null) > 0
  }

  override def duplicate(clazz: Class[_], id: Any, params: collection.Map[String, _]): Boolean = {
    duplicate(entityNameOf(clazz), id, params)
  }

  override def duplicate(entityName: String, id: Any, params: collection.Map[String, _]): Boolean = {
    val b = new StringBuilder("from ")
    b.append(entityName).append(" where ")
    val paramsMap = new mutable.HashMap[String, Any]
    var i = 0
    for ((key, value) <- params) {
      b.append(key).append('=').append(":param" + i)
      paramsMap.put("param" + i, value)
      i += 1
      if (i < params.size) b.append(" and ")
    }
    val list = search(b.toString, paramsMap).asInstanceOf[Seq[Entity[_]]]
    if (list.isEmpty) {
      false
    } else {
      if (id == null) true else list.exists(e => e.id != id)
    }
  }

  /**
    * 检查持久化对象是否存在e
    *
    * @return boolean(是否存在) 如果entityId为空或者有不一样的entity存在则认为存在。
    */
  override def duplicate[T <: Entity[_]](clazz: Class[T], id: Any, codeName: String, codeValue: Any): Boolean = {
    val list = findBy(clazz, codeName, List(codeValue))
    if (list.isEmpty) {
      false
    } else {
      if (id == null) true else list.exists(e => e.id != id)
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

  override def uniqueResult[T](builder: QueryBuilder[T]): T = {
    val list = search(builder.build())
    if (list.isEmpty) {
      null.asInstanceOf[T]
    } else if (list.size == 1) {
      list.head
    } else {
      throw new RuntimeException("not unique query" + builder)
    }
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

  override def evict(entity: AnyRef): Unit = {
    currentSession.evict(entity)
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
      case pc: PersistentCollection => Hibernate.initialize(pc)
    }
    rs
  }

  override def remove[E](entities: Iterable[E]): Unit = {
    if (null == entities || entities.isEmpty) return
    val session = currentSession
    for (entity <- entities; if null != entity)
      entity match {
        case seq: Iterable[_] => seq.foreach(session.delete)
        case _ => session.delete(entity)
      }
  }

  override def remove[E](first: E, entities: E*): Unit = {
    remove(first :: entities.toList)
  }

  override def remove[T <: Entity[ID], ID](clazz: Class[T], id: ID, ids: ID*): Unit = {
    removeBy(clazz, "id", id :: ids.toList)
  }

  def removeBy(clazz: Class[_], attr: String, first: Any, values: Any*): Boolean = {
    removeBy(clazz, attr, first :: values.toList)
  }

  def removeBy(clazz: Class[_], attr: String, values: Iterable[_]): Boolean = {
    if (clazz == null || Strings.isEmpty(attr) || values.isEmpty) return false
    val hql = new StringBuilder()
    hql.append("delete from ").append(entityNameOf(clazz)).append(" where ").append(attr).append(" in (:ids)")
    executeUpdate(hql.toString(), Map("ids" -> values)) > 0
  }

  def remove(clazz: Class[_], keyMap: collection.Map[String, _]): Boolean = {
    if (clazz == null || keyMap == null || keyMap.isEmpty) return false
    val hql = new StringBuilder()
    hql.append("delete from ").append(entityNameOf(clazz)).append(" where ")
    val params = new mutable.HashMap[String, Any]
    for ((keyName, keyValue) <- keyMap) {
      val paramName = keyName.replace('.', '_')
      params.put(paramName, keyValue)
      if (isCollectionType(keyValue.getClass)) {
        hql.append(keyName).append(" in (:").append(paramName).append(") and ")
      } else {
        hql.append(keyName).append(" = :").append(paramName).append(" and ")
      }
    }
    hql.append(" (1=1) ")
    executeUpdate(hql.toString(), params) > 0
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
        case Operation.SaveUpdate => persistEntity(operation.data, null)
        case Operation.Remove => remove(operation.data)
      }
    }
  }

  override def execute(builder: Operation.Builder): Unit = {
    for (operation <- builder.build()) {
      operation.typ match {
        case Operation.SaveUpdate => persistEntity(operation.data, null)
        case Operation.Remove => remove(operation.data)
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
          case col: Iterable[_] => for (elementEntry <- col) persistEntity(elementEntry, null)
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
          session.save(en, entity)
        } else {
          val si = session.asInstanceOf[SessionImplementor]
          if (si.getContextEntityIdentifier(entity) == null) {
            session.save(en, entity)
          } else {
            session.update(en, entity)
          }
        }
      case _ =>
        val en = if (null == entityName) entityNameOf(entity.getClass) else entityName
        session.saveOrUpdate(en, entity)
    }
  }

  def saveOrUpdate[T <: Entity[_]](entityName: String, entities: collection.Seq[T]): Unit = {
    if (entities.nonEmpty) {
      for (entity <- entities)
        persistEntity(entity, entityName)
    }
  }

  def saveOrUpdate[T <: Entity[_]](entityName: String, first: T, entities: T*): Unit = {
    saveOrUpdate(entityName, first :: entities.toList)
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

  def createBlob(inputStream: InputStream, length: Int): Blob = {
    Hibernate.getLobCreator(currentSession).createBlob(inputStream, length)
  }

  def createBlob(inputStream: InputStream): Blob = {
    val buffer = new ByteArrayOutputStream(inputStream.available())
    StreamUtils.copy(inputStream, buffer)
    Hibernate.getLobCreator(currentSession).createBlob(buffer.toByteArray)
  }

  def createClob(str: String): Clob = {
    Hibernate.getLobCreator(currentSession).createClob(str)
  }

  def isCollectionType(clazz: Class[_]): Boolean = {
    clazz.isArray ||
      classOf[java.util.Collection[_]].isAssignableFrom(clazz) ||
      classOf[scala.collection.Iterable[_]].isAssignableFrom(clazz)
  }

  /**
    * Support "@named-query" or "from object" styles query
    */
  private def getNamedOrCreateQuery[T](queryString: String): Query[T] = {
    if (queryString.charAt(0) == '@') currentSession.getNamedQuery(queryString.substring(1)).asInstanceOf[Query[T]]
    else currentSession.createQuery(queryString).asInstanceOf[Query[T]]
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
