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

package org.beangle.data.hibernate.proxy

import org.hibernate.HibernateException
import org.hibernate.engine.spi.{PrimeAmongSecondarySupertypes, SessionFactoryImplementor}
import org.hibernate.internal.SessionFactoryRegistry
import org.hibernate.internal.util.ReflectHelper.overridesEquals
import org.hibernate.proxy.{AbstractSerializableProxy, ProxyConfiguration}
import org.hibernate.`type`.CompositeType

import java.io.Serial
import java.lang.reflect.Method
import java.util.Objects

/** 未初始化懒加载代理的序列化载体。
 *
 * 由上游 `SerializableProxy` 改名而来；`readResolve` 不再依赖 bytebuddy（上游会强转
 * `BytecodeProviderImpl`），改为按命名约定加载构建期预生成的 `<Entity>$HibernateProxy` 类、
 * 反射还原 id getter/setter 后重建 [[BeangleInterceptor]] 并挂载。
 * 要求本 JVM 内存在 uuid/name 匹配的 SessionFactory（跨 JVM 反序列化未初始化代理不可用）。
 */
class BeangleSerializableProxy(
    entityName: String,
    persistentClass: Class[_],
    interfaces: Array[Class[_]],
    id: AnyRef,
    readOnly: java.lang.Boolean,
    sessionFactoryUuid: String,
    sessionFactoryName: String,
    allowLoadOutsideTransaction: Boolean,
    getIdentifierMethod: Method,
    setIdentifierMethod: Method,
    componentIdType: CompositeType)
  extends AbstractSerializableProxy(entityName, id, readOnly, sessionFactoryUuid, sessionFactoryName,
    allowLoadOutsideTransaction) {

  private val identifierGetterMethodName: String = Option(getIdentifierMethod).map(_.getName).orNull
  private val identifierGetterMethodClass: Class[_] = Option(getIdentifierMethod).map(_.getDeclaringClass).orNull

  private val identifierSetterMethodName: String = Option(setIdentifierMethod).map(_.getName).orNull
  private val identifierSetterMethodClass: Class[_] = Option(setIdentifierMethod).map(_.getDeclaringClass).orNull
  private val identifierSetterMethodParams: Array[Class[_]] = Option(setIdentifierMethod).map(_.getParameterTypes).orNull

  @Serial
  private def readResolve(): AnyRef = {
    val sessionFactory = retrieveMatchingSessionFactory()
    if sessionFactory == null then
      throw new IllegalStateException(s"Unable to deserialize uninitialized proxy [$getEntityName, $getId]: " +
        s"no matching SessionFactory with uuid '$sessionFactoryUuid' is registered in this JVM")

    val interceptor = new BeangleInterceptor(getEntityName, persistentClass, interfaces, getId,
      resolveIdGetterMethod(), resolveIdSetterMethod(), componentIdType, null, overridesEquals(persistentClass))

    val proxyClass =
      try Class.forName(s"${persistentClass.getName}$$HibernateProxy", true, persistentClass.getClassLoader)
      catch case e: ClassNotFoundException =>
        throw new HibernateException(s"Unable to deserialize proxy [$getEntityName, $getId]: prebuilt proxy class " +
          s"'${persistentClass.getName}$$HibernateProxy' was not found; ensure the entity was processed " +
          "by the beangle ProxyPlugin at build time", e)

    try
      val instance = proxyClass.getDeclaredConstructor().newInstance().asInstanceOf[PrimeAmongSecondarySupertypes]
      val proxyConfiguration = instance.asProxyConfiguration()
      if proxyConfiguration == null then
        throw new HibernateException("Produced proxy does not correctly implement ProxyConfiguration")
      proxyConfiguration.$$_hibernate_set_interceptor(interceptor)
      val hibernateProxy = instance.asHibernateProxy()
      if hibernateProxy == null then
        throw new HibernateException("Produced proxy does not correctly implement HibernateProxy")
      afterDeserialization(interceptor)
      hibernateProxy
    catch case t: Throwable =>
      throw new HibernateException(s"Unable to deserialize proxy [$getEntityName, $getId]", t)
  }

  private def retrieveMatchingSessionFactory(): SessionFactoryImplementor = {
    Objects.requireNonNull(sessionFactoryUuid)
    SessionFactoryRegistry.INSTANCE.findSessionFactory(sessionFactoryUuid, sessionFactoryName)
  }

  private def resolveIdGetterMethod(): Method =
    if identifierGetterMethodName == null then null
    else
      try identifierGetterMethodClass.getDeclaredMethod(identifierGetterMethodName)
      catch case e: NoSuchMethodException =>
        throw new HibernateException(s"Unable to deserialize proxy [$getEntityName, $getId]; could not locate id " +
          s"getter method [$identifierGetterMethodName] on entity class [$identifierGetterMethodClass]", e)

  private def resolveIdSetterMethod(): Method =
    if identifierSetterMethodName == null then null
    else
      try identifierSetterMethodClass.getDeclaredMethod(identifierSetterMethodName, identifierSetterMethodParams*)
      catch case e: NoSuchMethodException =>
        throw new HibernateException(s"Unable to deserialize proxy [$getEntityName, $getId]; could not locate id " +
          s"setter method [$identifierSetterMethodName] on entity class [$identifierSetterMethodClass]", e)
}
