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

package org.beangle.data.hibernate.tx

import org.aopalliance.aop.Advice
import org.aopalliance.intercept.{MethodInterceptor, MethodInvocation}
import org.beangle.data.hibernate.tx.TransactionalAdvisor.*
import org.springframework.aop.support.{AbstractPointcutAdvisor, AopUtils, StaticMethodMatcherPointcut}
import org.springframework.aop.{ClassFilter, Pointcut}
import org.springframework.dao.support.PersistenceExceptionTranslator
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.interceptor.{TransactionAspectSupport, TransactionAttribute, TransactionAttributeEditor, TransactionAttributeSource, TransactionalProxy}
import org.springframework.util.{ClassUtils, PatternMatchUtils}

import java.lang.reflect.Method
import java.util as ju
import scala.collection.immutable.Map

/**
 * 因为spring-tx中实现依赖spring-context:
 * - TransactionInterceptor 依赖 ApplicationEventPublisherAware (事件发布)
 * - setTransactionAttributes 创建的 NameMatchTransactionAttributeSource 依赖 EmbeddedValueResolverAware
 * 所以使用自定义实现，仅依赖 spring-tx + spring-aop
 */
object TransactionalAdvisor {

  /**
   * 根据目标类和 Properties 配置构建 TransactionAttributeSource。
   *
   * == 配置格式 ==
   *
   * Properties 的 key 为方法名或 pattern，value 为事务属性字符串（逗号分隔，顺序任意）：
   *
   * - 传播: `PROPAGATION_REQUIRED`(默认)、`PROPAGATION_SUPPORTS`、`PROPAGATION_MANDATORY`、
   *   `PROPAGATION_REQUIRES_NEW`、`PROPAGATION_NOT_SUPPORTED`、`PROPAGATION_NEVER`、`PROPAGATION_NESTED`
   * - 隔离: `ISOLATION_DEFAULT`(默认)、`ISOLATION_READ_UNCOMMITTED`、`ISOLATION_READ_COMMITTED`、
   *   `ISOLATION_REPEATABLE_READ`、`ISOLATION_SERIALIZABLE`
   * - 只读: `readOnly`
   * - 超时: `timeout_N`（秒），如 `timeout_30`
   * - 回滚: `-ExceptionName` 遇该异常回滚，`+ExceptionName` 遇该异常提交
   *
   * == Pattern 规则 ==
   *
   * key 支持通配符匹配方法名：
   *
   * - `get*` 匹配 getXxx、getById 等
   * - `*ById` 匹配 getById、findById 等
   * - `*xxx*` 匹配包含 xxx 的方法名
   * - 精确匹配：`save` 仅匹配 save
   *
   * 多个 pattern 命中时取最长的（更具体）映射。
   *
   * == 示例 ==
   *
   * {{{
   *   get*  = PROPAGATION_REQUIRED,readOnly
   *   save* = PROPAGATION_REQUIRED
   *   *ById = PROPAGATION_REQUIRED,readOnly
   * }}}
   *
   * @param clazz      目标类或接口（代理的类型）
   * @param properties 方法名/pattern -> 事务属性的配置
   * @return 可用于 TransactionalAdvisor 的 TransactionAttributeSource
   */
  def buildAttributeSource(clazz: Class[_], properties: ju.Properties): TransactionAttributeSource = {
    val methodMap = buildMethodAttributes(clazz, properties)
    new TxAttributeSource(methodMap)
  }

  private class TxAttributeSource(methodMap: Map[String, TransactionAttribute]) extends TransactionAttributeSource {
    override def getTransactionAttribute(method: Method, targetClass: Class[_]): TransactionAttribute = {
      methodMap.get(method.getName).orNull
    }
  }

  /** 执行事务逻辑
   *
   * @param ptm    事务管理器
   * @param source 属性配置
   */
  private class TxInterceptor(ptm: TransactionManager, source: TransactionAttributeSource) extends TransactionAspectSupport, MethodInterceptor {

    setTransactionManager(ptm)
    setTransactionAttributeSource(source)

    override def invoke(invocation: MethodInvocation): AnyRef = {
      val targetClass = if (invocation.getThis != null) AopUtils.getTargetClass(invocation.getThis) else null
      invokeWithinTransaction(invocation.getMethod, targetClass, new TransactionAspectSupport.InvocationCallback() {
        override def proceedWithInvocation: AnyRef = invocation.proceed
      })
    }
  }

  /** 判断哪些方法需要事务
   *
   * @param source
   */
  private class TxPointcut(val source: TransactionAttributeSource) extends StaticMethodMatcherPointcut {
    setClassFilter(new TxClassFilter(source))

    override def matches(method: Method, targetClass: Class[_]): Boolean =
      source == null || source.hasTransactionAttribute(method, targetClass)
  }

  private class TxClassFilter(source: TransactionAttributeSource) extends ClassFilter {
    override def matches(clazz: Class[_]): Boolean = {
      if (classOf[TransactionalProxy].isAssignableFrom(clazz)
        || classOf[TransactionManager].isAssignableFrom(clazz)
        || classOf[PersistenceExceptionTranslator].isAssignableFrom(clazz)) false
      else source == null || source.isCandidateClass(clazz)
    }
  }

  /**
   * 根据 targetClass 和 attributes 预解析，生成方法名到事务属性的不可变 Map。
   *
   * @param targetClass 目标类（代理的类或接口）
   * @param attributes  事务属性配置，如 "get*" -> "PROPAGATION_REQUIRED,readOnly", "save" -> "PROPAGATION_REQUIRED"
   * @return 方法名 -> TransactionAttribute 的不可变 Map
   */
  private def buildMethodAttributes(targetClass: Class[_], attributes: ju.Properties): Map[String, TransactionAttribute] = {
    val nameMap = parseProperties(attributes)
    val builder = Map.newBuilder[String, TransactionAttribute]
    for (method <- targetClass.getMethods if ClassUtils.isUserLevelMethod(method)) {
      val attr = findAttribute(method.getName, nameMap)
      if (attr != null) builder.addOne(method.getName -> attr)
    }
    builder.result()
  }

  private def parseProperties(attributes: ju.Properties): ju.Map[String, TransactionAttribute] = {
    val tae = new TransactionAttributeEditor
    val map = new ju.HashMap[String, TransactionAttribute]()
    val propNames = attributes.propertyNames()
    while (propNames.hasMoreElements) {
      val methodName = propNames.nextElement().asInstanceOf[String]
      val value = attributes.getProperty(methodName)
      tae.setAsText(value)
      val attr = tae.getValue.asInstanceOf[TransactionAttribute]
      map.put(methodName, attr)
    }
    map
  }

  private def findAttribute(methodName: String, nameMap: ju.Map[String, TransactionAttribute]): TransactionAttribute = {
    var attr = nameMap.get(methodName)
    if (attr == null) {
      var bestNameMatch: String = null
      val iter = nameMap.keySet.iterator
      while (iter.hasNext) {
        val mappedName = iter.next
        if (PatternMatchUtils.simpleMatch(mappedName, methodName) &&
          (bestNameMatch == null || mappedName.length > bestNameMatch.length)) {
          attr = nameMap.get(mappedName)
          bestNameMatch = mappedName
        }
      }
    }
    attr
  }
}

/** 声明式事务的切面入口，封装 Interceptor + Pointcut + Advisor 的组装。 */
class TransactionalAdvisor(tm: TransactionManager, source: TransactionAttributeSource) extends AbstractPointcutAdvisor {

  private val interceptor = new TxInterceptor(tm, source)
  private val pointcut = new TxPointcut(source)

  override def getAdvice: Advice = interceptor

  override def getPointcut: Pointcut = pointcut

}
