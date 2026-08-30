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

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.internal.CoreMessageLogger.CORE_LOGGER
import org.hibernate.internal.util.ReflectHelper.isPublic
import org.hibernate.proxy.ProxyConfiguration
import org.hibernate.proxy.pojo.BasicLazyInitializer
import org.hibernate.`type`.CompositeType

import java.lang.reflect.{InvocationTargetException, Method}

/** 懒加载代理拦截器：挂在构建期预生成的 `<Entity>$HibernateProxy` 代理上。
 *
 * 由上游 `ByteBuddyInterceptor` 改名而来（逻辑一致），继承 [[BasicLazyInitializer]] 并实现
 * [[ProxyConfiguration.Interceptor]]，不引用任何 `net.bytebuddy` 类。
 */
class BeangleInterceptor(
    entityName: String,
    persistentClass: Class[_],
    interfaces: Array[Class[_]],
    id: AnyRef,
    getIdentifierMethod: Method,
    setIdentifierMethod: Method,
    componentIdType: CompositeType,
    session: SharedSessionContractImplementor,
    overridesEquals: Boolean)
  extends BasicLazyInitializer(entityName, persistentClass, id, getIdentifierMethod, setIdentifierMethod,
    componentIdType, session, overridesEquals)
  with ProxyConfiguration.Interceptor {

  override def intercept(instance: AnyRef, method: Method, arguments: Array[AnyRef]): AnyRef =
    invoke(method, arguments, instance)

  override protected def call(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
    val target = getImplementation
    val returnValue =
      try
        if isPublic(persistentClass, method) then
          if !method.getDeclaringClass.isInstance(target) then
            throw new ClassCastException(
              target.getClass.getName + " incompatible with " + method.getDeclaringClass.getName)
          method.invoke(target, args*)
        else
          method.setAccessible(true)
          method.invoke(target, args*)
      catch case ite: InvocationTargetException => throw ite.getTargetException

    if returnValue eq target then
      val returnValueClass = returnValue.getClass
      if returnValueClass.isInstance(proxy) then proxy
      else
        CORE_LOGGER.narrowingProxy(returnValueClass)
        returnValue
    else returnValue
  }

  override protected def serializableProxy(): AnyRef =
    new BeangleSerializableProxy(getEntityName, persistentClass, interfaces, getInternalIdentifier,
      if isReadOnlySettingAvailable then java.lang.Boolean.valueOf(isReadOnly)
      else isReadOnlyBeforeAttachedToSession,
      getSessionFactoryUuid, getSessionFactoryName, isAllowLoadOutsideTransaction,
      getIdentifierMethod, setIdentifierMethod, componentIdType)
}
