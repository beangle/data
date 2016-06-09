/*
 * Beangle, Agile Development Scaffold and Toolkit
 *
 * Copyright (c) 2005-2016, Beangle Software.
 *
 * Beangle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Beangle is distributed in the hope that it will be useful.
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Beangle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.beangle.data.hibernate

import java.io.{ ByteArrayOutputStream, InputStream, Serializable }
import java.sql.{ Blob, Clob }
import scala.collection.JavaConversions.{ asScalaBuffer, mapAsJavaMap, seqAsJavaList, asJavaCollection }
import scala.collection.mutable
import scala.collection.immutable.Seq
import org.beangle.commons.collection.page.{ Page, PageLimit, SinglePage }
import org.beangle.commons.lang.{ Assert, Strings }
import org.beangle.commons.lang.annotation.description
import org.beangle.commons.logging.Logging
import org.beangle.data.model.Entity
import org.beangle.data.dao.{ Condition, EntityDao, LimitQuery, Operation, Query => BQuery, QueryBuilder, OqlBuilder }
import org.beangle.data.model.meta.EntityMetadata
import org.hibernate.{ Hibernate, Query }
import org.hibernate.{ SQLQuery, Session, SessionFactory }
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.jdbc.StreamUtils
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.proxy.HibernateProxy
import QuerySupport.{ doCount, doFind, list, setParameters }
import org.beangle.commons.collection.Wrappers

object QuerySupport {

  def list[T](query: Query): Seq[T] = {
    Wrappers.ImmutableJList(query.list().asInstanceOf[java.util.List[T]])
  }

  private def buildHibernateQuery(bquery: BQuery[_], session: Session): Query = {
    val query =
      if (bquery.lang.name == "Sql") session.createSQLQuery(bquery.statement)
      else session.createQuery(bquery.statement)
    if (bquery.cacheable) query.setCacheable(bquery.cacheable)
    setParameters(query, bquery.params)
    query
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
  def doFind[T](query: BQuery[_], session: Session): Seq[T] = {
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
  def setParameters(query: Query, argument: Any*): Query = {
    if (argument != null && argument.length > 0) {
      for (i <- 0 until argument.length)
        query.setParameter(String.valueOf(i + 1), argument(i).asInstanceOf[AnyRef])
    }
    query
  }

  /**
   * 为query设置参数
   */
  def setParameters(query: Query, parameterMap: collection.Map[String, _]): Query = {
    if (parameterMap != null && !parameterMap.isEmpty) {
      for ((k, v) <- parameterMap; if null != k) setParameter(query, k, v)
    }
    query
  }

  def setParameter(query: Query, param: String, value: Any): Query = {
    value match {
      case null                         => query.setParameter(param, null.asInstanceOf[AnyRef])
      case av: Array[AnyRef]            => query.setParameterList(param, av)
      case col: java.util.Collection[_] => query.setParameterList(param, col)
      case iter: Iterable[_]            => query.setParameterList(param, asJavaCollection(iter))
      case _                            => query.setParameter(param, value)
    }
    query
  }

  /**
   * 针对查询条件绑定查询的值
   */
  def bindValues(query: Query, conditions: List[Condition]) {
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
  import QuerySupport._

  val metadata: EntityMetadata = new EntityMetadataBuilder(List(sessionFactory)).build()

  protected def currentSession: Session = {
    sessionFactory.getCurrentSession()
  }

  override def get[T <: Entity[ID], ID](clazz: Class[T], id: ID): T = {
    (find(metadata.getType(clazz).get.entityName, id).orNull).asInstanceOf[T]
  }

  override def getAll[T](clazz: Class[T]): Seq[T] = {
    val hql = "from " + metadata.getType(clazz).orNull.entityName
    val query = currentSession.createQuery(hql)
    query.setCacheable(true)
    asScalaBuffer(query.list()).toList.asInstanceOf[List[T]]
  }

  def find[T <: Entity[ID], ID](entityName: String, id: ID): Option[T] = {
    if (Strings.contains(entityName, '.')) {
      val obj = currentSession.get(entityName, id.asInstanceOf[Serializable])
      if (null == obj) None else Some(obj.asInstanceOf[T])
    } else {
      val hql = "from " + entityName + " where id =:id"
      val query = currentSession.createQuery(hql)
      query.setParameter("id", id)
      val rs = query.list()
      if (rs.isEmpty()) None else Some(rs.get(0).asInstanceOf[T])
    }
  }

  override def find[T <: Entity[ID], ID](clazz: Class[T], id: ID): Option[T] = {
    find[T, ID](metadata.getType(clazz).get.entityName, id)
  }

  override def find[T <: Entity[ID], ID](entityClass: Class[T], ids: Iterable[ID]): Seq[T] = {
    findBy(metadata.getType(entityClass).get.entityName, "id", ids)
  }

  override def findBy[T <: Entity[_]](entityClass: Class[T], keyName: String, values: Iterable[_]): Seq[T] = {
    findBy(metadata.getType(entityClass).get.entityName, keyName, values)
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
      return search(query.params(parameterMap).build())
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
    if (clazz == null || parameterMap == null || parameterMap.isEmpty()) { return List.empty }
    val hql = new StringBuilder()
    hql.append("select entity from ").append(entityName(clazz)).append(" as entity ").append(" where ")

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
    if (rs.isEmpty) 0 else rs.get(0).asInstanceOf[Number].longValue
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
    search(hql.toString, params).get(0).asInstanceOf[Number].longValue
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

  override def duplicate(entityClass: Class[_], id: Any, params: collection.Map[String, _]): Boolean = {
    duplicate(metadata.getType(entityClass).get.entityName, id, params)
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
    if (query.isInstanceOf[LimitQuery[T]]) {
      val limitQuery = query.asInstanceOf[LimitQuery[T]]
      if (null == limitQuery.limit) {
        return doFind(limitQuery, currentSession)
      } else {
        new SinglePage[T](limitQuery.limit.pageIndex, limitQuery.limit.pageSize,
          doCount(limitQuery, currentSession), doFind(query, currentSession))
      }
    } else {
      return doFind(query, currentSession)
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
      list.get(0).asInstanceOf[T]
    } else {
      throw new RuntimeException("not unique query" + builder)
    }
  }

  override def search[T](query: String, params: Any*): Seq[T] = {
    list[T](setParameters(getNamedOrCreateQuery(query), params))
  }

  override def search[T](queryString: String, params: collection.Map[String, _]): Seq[T] = {
    list[T](setParameters(getNamedOrCreateQuery(queryString), params))
  }

  override def search[T](queryString: String, params: collection.Map[String, _], limit: PageLimit, cacheable: Boolean): Seq[T] = {
    val query = getNamedOrCreateQuery(queryString)
    query.setCacheable(cacheable)
    if (null == limit) list(setParameters(query, params))
    else paginateQuery(query, params, limit)
  }

  private def paginateQuery[T](query: Query, params: collection.Map[String, _], limit: PageLimit): Page[T] = {
    setParameters(query, params)
    query.setFirstResult((limit.pageIndex - 1) * limit.pageSize).setMaxResults(limit.pageSize)
    val targetList = query.list().asInstanceOf[java.util.List[T]]
    val queryStr = buildCountQueryStr(query)
    var countQuery: Query = null
    if (query.isInstanceOf[SQLQuery]) {
      countQuery = currentSession.createSQLQuery(queryStr)
    } else {
      countQuery = currentSession.createQuery(queryStr)
    }
    setParameters(countQuery, params)
    // 返回结果
    new SinglePage[T](limit.pageIndex, limit.pageSize, countQuery.uniqueResult().asInstanceOf[Number].intValue, asScalaBuffer(targetList))
  }

  override def evict(entity: AnyRef): Unit = {
    currentSession.evict(entity)
  }

  override def refresh[T](entity: T): T = {
    currentSession.refresh(entity);
    entity
  }

  override def initialize[T](proxy: T): T = {
    var rs = proxy
    proxy match {
      case hp: HibernateProxy =>
        val initer = hp.getHibernateLazyInitializer()
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
    for (entity <- entities; if (null != entity))
      entity match {
        case seq: Seq[_] =>
          for (o <- seq)
            session.delete(o)
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
    if (clazz == null || Strings.isEmpty(attr) || values.size == 0) return false
    val hql = new StringBuilder()
    hql.append("delete from ").append(entityName(clazz)).append(" where ").append(attr).append(" in (:ids)")
    executeUpdate(hql.toString(), Map("ids" -> values)) > 0
  }

  def remove(clazz: Class[_], keyMap: collection.Map[String, _]): Boolean = {
    if (clazz == null || keyMap == null || keyMap.isEmpty) return false
    val hql = new StringBuilder()
    hql.append("delete from ").append(entityName(clazz)).append(" where ")
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
    setParameters(getNamedOrCreateQuery(queryString), parameterMap).executeUpdate()
  }

  override def executeUpdate(queryString: String, arguments: Any*): Int = {
    setParameters(getNamedOrCreateQuery(queryString), arguments).executeUpdate()
  }

  override def executeUpdateRepeatly(queryString: String, arguments: Iterable[Iterable[_]]): List[Int] = {
    val query = getNamedOrCreateQuery(queryString)
    val updates = new mutable.ListBuffer[Int]
    var i = 0
    for (params <- arguments) {
      updates += setParameters(query, params).executeUpdate()
    }
    updates.toList
  }

  override def execute(opts: Operation*): Unit = {
    for (operation <- opts) {
      operation.typ match {
        case Operation.SaveUpdate => persistEntity(operation.data, null)
        case Operation.Remove     => remove(operation.data)
      }
    }
  }

  override def execute(builder: Operation.Builder): Unit = {
    for (operation <- builder.build()) {
      operation.typ match {
        case Operation.SaveUpdate => persistEntity(operation.data, null)
        case Operation.Remove     => remove(operation.data)
      }
    }
  }

  override def saveOrUpdate[E](first: E, entities: E*): Unit = {
    saveOrUpdate(first :: entities.toList)
  }

  override def saveOrUpdate[E](entities: Iterable[E]): Unit = {
    if (!entities.isEmpty) {
      for (entity <- entities)
        entity match {
          case col: Seq[_] => for (elementEntry <- col) persistEntity(elementEntry, null)
          case _           => persistEntity(entity, null)
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
        val en = if (null == entityName) this.entityName(entity.getClass) else entityName
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
        val en = if (null == entityName) this.entityName(entity.getClass) else entityName
        session.saveOrUpdate(en, entity)
    }
  }

  def saveOrUpdate[T <: Entity[_]](entityName: String, entities: Seq[T]): Unit = {
    if (!entities.isEmpty()) {
      for (entity <- entities)
        persistEntity(entity, entityName)
    }
  }

  def saveOrUpdate[T <: Entity[_]](entityName: String, first: T, entities: T*): Unit = {
    saveOrUpdate(entityName, first :: entities.toList)
  }

  // update entityClass set [argumentName=argumentValue,]* where attr in values
  def batchUpdate(entityClass: Class[_], attr: String, values: Iterable[_], argumentNames: Seq[String], argumentValues: Seq[Any]): Int = {
    if (values.isEmpty) return 0
    val updateParams = new mutable.HashMap[String, Any]
    for (i <- 0 until argumentValues.size) {
      updateParams.put(argumentNames(i), argumentValues(i))
    }
    batchUpdate(entityClass, attr, values, updateParams)
  }

  def batchUpdate(entityClass: Class[_], attr: String, values: Iterable[_], updateParams: scala.collection.Map[String, _]): Int = {
    if (values.isEmpty || updateParams.isEmpty) return 0
    val hql = new StringBuilder()
    hql.append("update ").append(entityName(entityClass)).append(" set ")
    val newParams = new mutable.HashMap[String, Any]
    for ((parameterName, value) <- updateParams; if (null != parameterName)) {
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
    Hibernate.getLobCreator(currentSession).createBlob(buffer.toByteArray())
  }

  def createClob(str: String): Clob = {
    Hibernate.getLobCreator(currentSession).createClob(str)
  }

  protected def entityName(clazz: Class[_]): String = {
    metadata.getType(clazz) match {
      case Some(e) => e.entityName
      case None    => throw new RuntimeException(s"Cannot find ${clazz.getName} entity")
    }
  }

  def isCollectionType(clazz: Class[_]): Boolean = {
    clazz.isArray || clazz.isInstanceOf[java.util.Collection[_]] || clazz.isInstanceOf[scala.collection.Iterable[_]]
  }

  /**
   * Support "@named-query" or "from object" styles query
   */
  private def getNamedOrCreateQuery(queryString: String): Query = {
    if (queryString.charAt(0) == '@') currentSession.getNamedQuery(queryString.substring(1))
    else currentSession.createQuery(queryString)
  }

  /**
   * 构造查询记录数目的查询字符串
   */
  private def buildCountQueryStr(query: Query): String = {
    var queryStr = "select count(*) "
    if (query.isInstanceOf[SQLQuery]) {
      queryStr += ("from (" + query.getQueryString() + ")")
    } else {
      val lowerCaseQueryStr = query.getQueryString().toLowerCase()
      val selectWhich = lowerCaseQueryStr.substring(0, query.getQueryString().indexOf("from"))
      val indexOfDistinct = selectWhich.indexOf("distinct")
      val indexOfFrom = lowerCaseQueryStr.indexOf("from")
      // 如果含有distinct
      if (-1 != indexOfDistinct) {
        if (Strings.contains(selectWhich, ",")) {
          queryStr = "select count(" + query.getQueryString().substring(indexOfDistinct, query.getQueryString().indexOf(",")) + ")"
        } else {
          queryStr = "select count(" + query.getQueryString().substring(indexOfDistinct, indexOfFrom) + ")"
        }
      }
      queryStr += query.getQueryString().substring(indexOfFrom)
    }
    queryStr
  }

}
