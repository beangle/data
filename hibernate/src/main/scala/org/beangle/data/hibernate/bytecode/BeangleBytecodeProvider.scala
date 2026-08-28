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

package org.beangle.data.hibernate.bytecode

import org.hibernate.HibernateException
import org.hibernate.bytecode.enhance.spi.{EnhancementContext, Enhancer}
import org.hibernate.bytecode.spi.{BasicProxyFactory, BytecodeProvider, ProxyFactoryFactory, ReflectionOptimizer}
import org.hibernate.engine.spi.{SessionFactoryImplementor, SharedSessionContractImplementor}
import org.hibernate.internal.util.ReflectHelper
import org.hibernate.property.access.spi.PropertyAccess
import org.hibernate.proxy.{HibernateProxy, ProxyConfiguration, ProxyFactory}
import org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor
import org.hibernate.`type`.CompositeType

import java.lang.reflect.Method

/** 按名加载构建期预生成代理类的 Hibernate BytecodeProvider。
 *
 * 代理类在构建期由 [[org.beangle.data.hibernate.aot.BeangleProxyGenerator]] 生成并打进 jar，
 * 类名固定为 `<Entity>$HibernateProxy`；运行期（JVM 与 native 同路径）不再需要 ByteBuddy：
 * 本 provider 不引用任何 net.bytebuddy 类，`getProxy` 仅做无参实例化并挂上
 * `ByteBuddyInterceptor`（fork jar 内无 bytebuddy 引用的拦截器）。
 *
 * 通过 `META-INF/services/org.hibernate.bytecode.spi.BytecodeProvider` 注册，
 * 取代 hibernate-core 默认的 bytebuddy provider（fork 已从 shaded jar 剔除该 SPI）。
 */
class BeangleBytecodeProvider extends BytecodeProvider {

  override def getProxyFactoryFactory(): ProxyFactoryFactory = new BeangleProxyFactoryFactory

  override def getReflectionOptimizer(clazz: Class[_], getterNames: Array[String], setterNames: Array[String],
      types: Array[Class[_]]): ReflectionOptimizer = {
    throw new HibernateException(
      "Using the ReflectionOptimizer is not possible when the configured BytecodeProvider is 'beangle'")
  }

  override def getReflectionOptimizer(clazz: Class[_], propertyAccessMap: java.util.Map[String, PropertyAccess]): ReflectionOptimizer = null

  override def getEnhancer(enhancementContext: EnhancementContext): Enhancer = null
}

class BeangleProxyFactoryFactory extends ProxyFactoryFactory {

  override def buildProxyFactory(sessionFactory: SessionFactoryImplementor): ProxyFactory = new BeangleProxyFactory

  override def buildBasicProxyFactory(superClassOrInterface: Class[_]): BasicProxyFactory =
    new BeangleBasicProxyFactory(superClassOrInterface)
}

/** 与 hibernate 的 `none` provider 一致：beangle 实体均为具体类，不支持接口/抽象组件的基本代理。 */
class BeangleBasicProxyFactory(superClassOrInterface: Class[_]) extends BasicProxyFactory {

  override def getProxy(): AnyRef =
    throw new HibernateException(
      s"BeangleBasicProxyFactory is unable to generate a BasicProxy for type $superClassOrInterface. Enable a different BytecodeProvider.")
}

/** 与 hibernate `ByteBuddyProxyFactory` 同协议，仅把"构建期生成代理类"换成"按命名约定加载"：
 * `postInstantiate` 记录实体信息并按 `<Entity>$HibernateProxy` 定位代理类，
 * `getProxy` 无参实例化代理并挂上 `ByteBuddyInterceptor`（运行期零字节码生成）。
 */
class BeangleProxyFactory extends ProxyFactory with Serializable {

  private var entityName: String = _
  private var persistentClass: Class[_] = _
  private var interfaces: Array[Class[_]] = _
  private var getIdentifierMethod: Method = _
  private var setIdentifierMethod: Method = _
  private var componentIdType: CompositeType = _
  private var overridesEquals: Boolean = _
  private var proxyClass: Class[_] = _

  override def postInstantiate(entityName: String, persistentClass: Class[_], interfaces: java.util.Set[Class[_]],
      getIdentifierMethod: Method, setIdentifierMethod: Method, componentIdType: CompositeType): Unit = {
    this.entityName = entityName
    this.persistentClass = persistentClass
    this.interfaces = interfaces.toArray(new Array[Class[_]](interfaces.size()))
    this.getIdentifierMethod = getIdentifierMethod
    this.setIdentifierMethod = setIdentifierMethod
    this.componentIdType = componentIdType
    this.overridesEquals = ReflectHelper.overridesEquals(persistentClass)
    proxyClass = Class.forName(persistentClass.getName + "$HibernateProxy", false, persistentClass.getClassLoader)
  }

  override def getProxy(id: Any, session: SharedSessionContractImplementor): HibernateProxy = {
    val interceptor = new ByteBuddyInterceptor(entityName, persistentClass, interfaces, id,
      getIdentifierMethod, setIdentifierMethod, componentIdType, session, overridesEquals)
    try {
      val proxy = proxyClass.getConstructor().newInstance().asInstanceOf[ProxyConfiguration]
      proxy.$$_hibernate_set_interceptor(interceptor)
      proxy.asInstanceOf[HibernateProxy]
    } catch {
      case e: Exception =>
        throw new HibernateException(s"Failed to instantiate the pre-generated Hibernate proxy for $entityName", e)
    }
  }
}
